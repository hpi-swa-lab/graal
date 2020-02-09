/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.tools.lsp.server.request;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.IndexRange;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.tools.lsp.definitions.*;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.exceptions.EvaluationResultException;
import org.graalvm.tools.lsp.exceptions.InvalidCoverageScriptURI;
import org.graalvm.tools.lsp.exceptions.UnknownLanguageException;
import org.graalvm.tools.lsp.instrument.LSPInstrument;
import org.graalvm.tools.lsp.parsing.SourceToBabylonParser;
import org.graalvm.tools.lsp.server.ContextAwareExecutor;
import org.graalvm.tools.lsp.server.types.Diagnostic;
import org.graalvm.tools.lsp.server.types.DiagnosticSeverity;
import org.graalvm.tools.lsp.server.types.Range;
import org.graalvm.tools.lsp.server.utils.*;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.graalvm.tools.lsp.parsing.SourceToBabylonParser.*;


public final class SourceCodeEvaluator extends AbstractRequestHandler {
    private static final TruffleLogger LOG = TruffleLogger.getLogger(LSPInstrument.ID, SourceCodeEvaluator.class);
    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    public SourceCodeEvaluator(TruffleInstrument.Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor executor) {
        super(env, surrogateMap, executor);
    }

    private static EvaluationResult evalLiteral(Node nearestNode) {
        Object nodeObject = ((InstrumentableNode) nearestNode).getNodeObject();
        if (nodeObject instanceof TruffleObject) {
            try {
                if (INTEROP.isMemberReadable(nodeObject, "literal")) {
                    Object result = INTEROP.readMember(nodeObject, "literal");
                    if (result instanceof TruffleObject || InteropUtils.isPrimitive(result)) {
                        return EvaluationResult.createResult(result);
                    } else {
                        LOG.log(Level.FINE, "Literal is no TruffleObject or primitive: {0}", result.getClass());
                    }
                }
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                LOG.warning(e.getMessage());
                return EvaluationResult.createError(e);
            }
        }
        return EvaluationResult.createEvaluationSectionNotReached();
    }

    private static String findLanguageOfTestFile(URI runScriptUri, String fallbackLangId) {
        try {
            return Source.findLanguage(runScriptUri.toURL());
        } catch (IOException e) {
            return fallbackLangId;
        }
    }

    /**
     * A special method to create a {@link SourceSectionFilter} which filters for a specific source
     * section during source code evaluation. We cannot simply filter with
     * {@link Builder#sourceIs(Source...)} and {@link Builder#sourceSectionEquals(SourceSection...)}
     * , because we are possibly not the creator of the Source and do not know which properties are
     * set. The source which is evaluated could have been created by the language. For example by a
     * Python import statement. Therefore we need to filter via URI (or name if the URI is a
     * generated truffle-schema-URI).
     *
     * @param uri           to filter sources for
     * @param sourceSection to filter for with same start and end indices
     * @return a builder to add further filter options
     */
    static SourceSectionFilter.Builder createSourceSectionFilter(URI uri, SourceSection sourceSection) {
        return SourceSectionFilter.newBuilder() //
                .lineStartsIn(IndexRange.between(sourceSection.getStartLine(), sourceSection.getStartLine() + 1)) //
                .lineEndsIn(IndexRange.between(sourceSection.getEndLine(), sourceSection.getEndLine() + 1)) //
                .columnStartsIn(IndexRange.between(sourceSection.getStartColumn(), sourceSection.getStartColumn() + 1)) //
                .columnEndsIn(IndexRange.between(sourceSection.getEndColumn(), sourceSection.getEndColumn() + 1)) //
                .sourceIs(SourcePredicateBuilder.newBuilder().uriOrTruffleName(uri).build());
    }

    static List<CoverageData> findCoverageDataBeforeNode(TextDocumentSurrogate surrogate, Node targetNode) {
        List<CoverageData> coveragesBeforeNode = new ArrayList<>();
        targetNode.getRootNode().accept(new NodeVisitor() {
            boolean found = false;

            @Override
            public boolean visit(Node node) {
                if (found) {
                    return false;
                }

                if (node.equals(targetNode)) {
                    found = true;
                    return false;
                }

                SourceSection sourceSection = node.getSourceSection();
                if (sourceSection != null && sourceSection.isAvailable()) {
                    List<CoverageData> coverageData = surrogate.getCoverageData(sourceSection);
                    if (coverageData != null) {
                        coveragesBeforeNode.addAll(coverageData);
                    }
                }

                return true;
            }
        });
        return coveragesBeforeNode;
    }

    private Map<SourceSection, LanguageAgnosticFunctionDeclarationDefinition> getFunctionDeclarations(SourceSectionFilter.SourcePredicate srcPredicate, URI uri) {
        Map<SourceSection, LanguageAgnosticFunctionDeclarationDefinition> functionDeclarations = new LinkedHashMap<>();
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().sourceIs(srcPredicate).build();
        env.getInstrumenter().visitLoadedSourceSections(filter, event -> {
            Node node = event.getNode();
            if (!(node instanceof InstrumentableNode)) {
                return;
            }
            InstrumentableNode instrumentableNode = (InstrumentableNode) node;
            LinkedList<Scope> scopesOuterToInner = getScopesOuterToInner(surrogateMap.get(uri), instrumentableNode);
            scopesOuterToInner.forEach((scope -> {
                if (scope.getName().equals(":program")) {
                    return;
                }

                InteropLibrary interopLibrary = InteropLibrary.getFactory().createDispatched(5);

                List<LanguageAgnosticFunctionArgumentDefinition> arguments = new ArrayList<>();

                try {
                    Object argumentMembers = interopLibrary.getMembers(scope.getArguments());

                    long argumentCount = interopLibrary.getArraySize(argumentMembers);

                    for (long i = 0; i < argumentCount; i++) {
                        String argumentName = interopLibrary.readArrayElement(argumentMembers, i).toString();
                        arguments.add(new LanguageAgnosticFunctionArgumentDefinition(argumentName, null));
                    }
                } catch (UnsupportedMessageException | InvalidArrayIndexException | NullPointerException e) {
                    // Failed to parse arguments return none
                }

                LanguageAgnosticFunctionDeclarationDefinition function = new LanguageAgnosticFunctionDeclarationDefinition(
                        scope.getName(),
                        scope.getNode().getSourceSection().getStartLine(),
                        scope.getNode().getSourceSection().getEndLine(),
                        arguments
                );
                functionDeclarations.put(scope.getNode().getSourceSection(), function);
            }));
        });
        return functionDeclarations;
    }

    public CallTarget parse(final TextDocumentSurrogate surrogate) throws DiagnosticsNotification {
        if (!env.getLanguages().containsKey(surrogate.getLanguageId())) {
            throw new UnknownLanguageException("Unknown language: " + surrogate.getLanguageId() + ". Known languages are: " + env.getLanguages().keySet());
        }

        SourceWrapper sourceWrapper = surrogate.prepareParsing();
        CallTarget callTarget = null;
        try {
            LOG.log(Level.FINE, "Parsing {0} {1}", new Object[]{surrogate.getLanguageId(), surrogate.getUri()});
            callTarget = env.parse(sourceWrapper.getSource());
            LOG.log(Level.FINER, "Parsing done.");

            LOG.log(Level.FINE, "Extracting function symbols of file with URI {0}", new Object[]{surrogate.getUri()});
            URI uri = surrogate.getUri();
            SourceSectionFilter.SourcePredicate srcPredicate = newDefaultSourcePredicateBuilder().uriOrTruffleName(uri).build();
            Map<SourceSection, LanguageAgnosticFunctionDeclarationDefinition> functionDeclarations = getFunctionDeclarations(srcPredicate, uri);
            surrogate.setFunctionDeclarationDefinitionMap(functionDeclarations);
            LOG.log(Level.FINER, "Extraction of function symbols done.");
        } catch (Exception e) {
            if (e instanceof TruffleException) {
                throw DiagnosticsNotification.create(surrogate.getUri(),
                        Diagnostic.create(SourceUtils.getRangeFrom((TruffleException) e), e.getMessage(), DiagnosticSeverity.Error, null, "Graal", null));
            } else {
                // TODO(ds) throw an Exception which the LSPServer can catch to send a client
                // notification
                throw new RuntimeException(e);
            }
        } finally {
            surrogate.notifyParsingDone(callTarget);
        }

        return callTarget;
    }

    public ExampleDefinition evaluateProbesAndAssertionsForExample(URI uri, ExampleDefinition example) throws DiagnosticsNotification {
        TextDocumentSurrogate originalSurrogate = surrogateMap.get(uri);

        TextDocumentSurrogate surrogate = originalSurrogate.copy();
        surrogate.setEditorText(surrogate.getEditorText() + "\n" + example.getFunctionName());
        final CallTarget callTarget = parse(surrogate);

        List<EventBinding<?>> eventBindingList = new ArrayList<>();

            SourceSectionFilter sourceSectionFilter = SourceSectionFilter.
                    newBuilder().
                    tagIs(StandardTags.StatementTag.class).
                    build();
            eventBindingList.add(env.getInstrumenter().attachExecutionEventListener(sourceSectionFilter, new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext context, VirtualFrame frame) {
                    // Do nothing
                }

                @Override
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    SourceSection sourceSection = context.getInstrumentedSourceSection();
                    String uri = sourceSection.getSource().getName();

                    if (result == null) {
                        return;
                    }

                    // TODO Find a better way of enabling cross-file probing
                    if (!uri.equals(example.getUri())) {
                        uri = example.getUri().substring(0, example.getUri().lastIndexOf("/") + 1) + uri;
                    }


                    String explicitProbeAnnotation = sourceSection.getSource().getCharacters(sourceSection.getStartLine() -1).toString();
                    if (example.getProbeMode() == ExampleDefinition.ProbeMode.ALL ||
                        (example.getProbeMode() == ExampleDefinition.ProbeMode.DEFAULT && explicitProbeAnnotation.trim().equals("// <Probe />"))) {
                        ProbeDefinition probe = new ProbeDefinition(sourceSection.getStartLine());
                        example.getProbes().add(probe);
                        probe.setResult(result);
                        probe.setUri(uri);
                        probe.setStartColumn(sourceSection.getStartColumn());
                        probe.setEndColumn(sourceSection.getEndColumn());
                    }

                    if (example.getProbeMode() == ExampleDefinition.ProbeMode.ALL || example.getProbeMode() == ExampleDefinition.ProbeMode.DEFAULT) {
                        String assertionAnnotationPattern = "// <(Assertion[a-zA-Z0-9_]*) (.*)\\/>";
                        Matcher m = Pattern.compile(assertionAnnotationPattern).matcher(explicitProbeAnnotation);
                        while (m.find()) {
                            String exampleForAssertion = m.group(2).trim().split(" ")[0].split("=")[1];
                            if (!example.getExampleName().equals(exampleForAssertion)) {
                                continue;
                            }
                            String expectedValue = m.group(2).trim().split(" ")[1].split("=")[1];
                            Object expectedValueObject = SourceToBabylonParser.convertExpectedValueType(expectedValue);
                            AssertionDefinition assertion = new AssertionDefinition(sourceSection.getStartLine(), expectedValueObject);
                            example.getAssertions().add(assertion);
                            assertion.setResult(result);
                            assertion.setUri(uri);
                            assertion.setStartColumn(sourceSection.getStartColumn());
                            assertion.setEndColumn(sourceSection.getEndColumn());
                        }
                    }
                }

                @Override
                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    // Do nothing
                }
            }));

        Object exampleResult;
        try {
            exampleResult = callTarget.call();
        } catch (Exception e) {
            exampleResult = e.getMessage();
        }
        example.setExampleResult(exampleResult);

        eventBindingList.forEach(EventBinding::dispose);

        return example;
    }

    public EvaluationResult tryDifferentEvalStrategies(TextDocumentSurrogate surrogate, Node nearestNode) throws DiagnosticsNotification {
        LOG.fine("Trying literal eval...");
        EvaluationResult literalResult = evalLiteral(nearestNode);
        if (literalResult.isEvaluationDone() && !literalResult.isError()) {
            return literalResult;
        }

        EvaluationResult coverageEvalResult = evalWithCoverageData(surrogate, nearestNode);
        if (coverageEvalResult.isEvaluationDone() && !coverageEvalResult.isError()) {
            return coverageEvalResult;
        }

        LOG.fine("Trying run-to-section eval...");
        EvaluationResult runToSectionEvalResult = runToSectionAndEval(surrogate, nearestNode);
        if (runToSectionEvalResult.isEvaluationDone()) {
            return runToSectionEvalResult;
        }

        LOG.fine("Trying global eval...");
        EvaluationResult globalScopeEvalResult = evalInGlobalScope(surrogate.getLanguageId(), nearestNode);
        if (globalScopeEvalResult.isError()) {
            return EvaluationResult.createEvaluationSectionNotReached();
        }
        return globalScopeEvalResult;
    }

    private EvaluationResult evalWithCoverageData(TextDocumentSurrogate textDocumentSurrogate, Node nearestNode) {
        if (!textDocumentSurrogate.hasCoverageData()) {
            return EvaluationResult.createEvaluationSectionNotReached();
        }

        List<CoverageData> dataBeforeNode = findCoverageDataBeforeNode(textDocumentSurrogate, nearestNode);
        if (dataBeforeNode == null || dataBeforeNode.isEmpty()) {
            return EvaluationResult.createEvaluationSectionNotReached();
        }

        CoverageData coverageData = dataBeforeNode.get(dataBeforeNode.size() - 1);
        if (((InstrumentableNode) nearestNode).hasTag(StandardTags.ReadVariableTag.class)) {
            // Shortcut for variables
            List<? extends FrameSlot> slots = coverageData.getFrame().getFrameDescriptor().getSlots();
            String symbol = nearestNode.getSourceSection().getCharacters().toString();
            FrameSlot frameSlot = slots.stream().filter(slot -> slot.getIdentifier().equals(symbol)).findFirst().orElseGet(() -> null);
            if (frameSlot != null) {
                LOG.fine("Coverage-based variable look-up");
                Object frameSlotValue = coverageData.getFrame().getValue(frameSlot);
                return EvaluationResult.createResult(frameSlotValue);
            }
        }

        LanguageInfo info = nearestNode.getRootNode().getLanguageInfo();
        String code = nearestNode.getSourceSection().getCharacters().toString();
        Source inlineEvalSource = Source.newBuilder(info.getId(), code, "in-line eval (hover request)").cached(false).build();
        ExecutableNode executableNode = env.parseInline(inlineEvalSource, nearestNode, coverageData.getFrame());

        CoverageEventNode coverageEventNode = coverageData.getCoverageEventNode();
        coverageEventNode.insertOrReplaceChild(executableNode);

        try {
            LOG.fine("Trying coverage-based eval...");
            Object result = executableNode.execute(coverageData.getFrame());
            return EvaluationResult.createResult(result);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            coverageEventNode.clearChild();
        }
        return EvaluationResult.createEvaluationSectionNotReached();
    }

    public EvaluationResult runToSectionAndEval(final TextDocumentSurrogate surrogate, final Node nearestNode) throws DiagnosticsNotification {
        if (!(nearestNode instanceof InstrumentableNode) || !((InstrumentableNode) nearestNode).isInstrumentable()) {
            return EvaluationResult.createEvaluationSectionNotReached();
        }

        SourceSectionFilter eventFilter = createSourceSectionFilter(surrogate.getUri(), nearestNode.getSourceSection()).build();
        return runToSectionAndEval(surrogate, nearestNode.getSourceSection(), eventFilter, null);
    }

    public EvaluationResult runToSectionAndEval(final TextDocumentSurrogate surrogate, final SourceSection sourceSection, SourceSectionFilter eventFilter, SourceSectionFilter inputFilter)
            throws DiagnosticsNotification {
        Set<URI> coverageUris = surrogate.getCoverageUris(sourceSection);
        URI runScriptUriFallback = coverageUris == null ? null : coverageUris.stream().findFirst().orElseGet(() -> null);
        TextDocumentSurrogate surrogateOfTestFile = createSurrogateForTestFile(surrogate, runScriptUriFallback);
        final CallTarget callTarget = parse(surrogateOfTestFile);
        final boolean isInputFilterDefined = inputFilter != null;

        EventBinding<ExecutionEventNodeFactory> binding = env.getInstrumenter().attachExecutionEventFactory(
                eventFilter,
                inputFilter,
                new ExecutionEventNodeFactory() {
                    StringBuilder indent = new StringBuilder("");

                    @Override
                    public ExecutionEventNode create(EventContext context) {
                        return new ExecutionEventNode() {

                            private String sourceSectionFormat(SourceSection section) {
                                return "SourceSection(" + section.getCharacters().toString().replaceAll("\n", Matcher.quoteReplacement("\\n")) + ")";
                            }

                            @Override
                            public void onReturnValue(VirtualFrame frame, Object result) {
                                if (LOG.isLoggable(Level.FINEST)) {
                                    logOnReturnValue(result);
                                }

                                if (!isInputFilterDefined) {
                                    CompilerDirectives.transferToInterpreter();
                                    throw new EvaluationResultException(result);
                                }
                            }

                            @TruffleBoundary
                            private void logOnReturnValue(Object result) {
                                if (indent.length() > 1) {
                                    indent.setLength(indent.length() - 2);
                                }
                                LOG.log(Level.FINEST, "{0}onReturnValue {1} {2} {3} {4}", new Object[]{indent, context.getInstrumentedNode().getClass().getSimpleName(),
                                        sourceSectionFormat(context.getInstrumentedSourceSection()), result});
                            }

                            @Override
                            public void onReturnExceptional(VirtualFrame frame, Throwable exception) {
                                if (LOG.isLoggable(Level.FINEST)) {
                                    logOnReturnExceptional();
                                }
                            }

                            @TruffleBoundary
                            private void logOnReturnExceptional() {
                                indent.setLength(indent.length() - 2);
                                LOG.log(Level.FINEST, "{0}onReturnExceptional {1}", new Object[]{indent, sourceSectionFormat(context.getInstrumentedSourceSection())});
                            }

                            @Override
                            public void onEnter(VirtualFrame frame) {
                                if (LOG.isLoggable(Level.FINEST)) {
                                    logOnEnter();
                                }
                            }

                            @TruffleBoundary
                            private void logOnEnter() {
                                LOG.log(Level.FINEST, "{0}onEnter {1} {2}", new Object[]{indent, context.getInstrumentedNode().getClass().getSimpleName(),
                                        sourceSectionFormat(context.getInstrumentedSourceSection())});
                                indent.append("  ");
                            }

                            @Override
                            public void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex, Object inputValue) {
                                if (LOG.isLoggable(Level.FINEST)) {
                                    logOnInputValue(inputContext, inputIndex, inputValue);
                                }
                                CompilerDirectives.transferToInterpreter();
                                throw new EvaluationResultException(inputValue);
                            }

                            @TruffleBoundary
                            private void logOnInputValue(EventContext inputContext, int inputIndex, Object inputValue) {
                                indent.setLength(indent.length() - 2);
                                LOG.log(Level.FINEST, "{0}onInputValue idx:{1} {2} {3} {4} {5} {6}",
                                        new Object[]{indent, inputIndex, inputContext.getInstrumentedNode().getClass().getSimpleName(),
                                                sourceSectionFormat(context.getInstrumentedSourceSection()),
                                                sourceSectionFormat(inputContext.getInstrumentedSourceSection()), inputValue,
                                                env.findMetaObject(inputContext.getInstrumentedNode().getRootNode().getLanguageInfo(), inputValue)});
                                indent.append("  ");
                            }
                        };
                    }

                });

        try {
            callTarget.call();
        } catch (EvaluationResultException e) {
            return e.isError() ? EvaluationResult.createError(e.getResult()) : EvaluationResult.createResult(e.getResult());
        } catch (RuntimeException e) {
            if (e instanceof TruffleException) {
                if (((TruffleException) e).isExit()) {
                    return EvaluationResult.createEvaluationSectionNotReached();
                } else {
                    return EvaluationResult.createError(e);
                }
            }
        } finally {
            binding.dispose();
        }
        return EvaluationResult.createEvaluationSectionNotReached();
    }

    public TextDocumentSurrogate createSurrogateForTestFile(TextDocumentSurrogate surrogateOfOpenedFile, URI runScriptUriFallback) throws DiagnosticsNotification {
        URI runScriptUri;
        try {
            runScriptUri = RunScriptUtils.extractScriptPath(surrogateOfOpenedFile);
        } catch (InvalidCoverageScriptURI e) {
            throw DiagnosticsNotification.create(surrogateOfOpenedFile.getUri(),
                    Diagnostic.create(Range.create(0, e.getIndex(), 0, e.getLength()), e.getReason(), DiagnosticSeverity.Error, null, "Graal LSP", null));
        }

        if (runScriptUri == null) {
            runScriptUri = runScriptUriFallback != null ? runScriptUriFallback : surrogateOfOpenedFile.getUri();
        }

        final String langIdOfTestFile = findLanguageOfTestFile(runScriptUri, surrogateOfOpenedFile.getLanguageId());
        LanguageInfo languageInfo = env.getLanguages().get(langIdOfTestFile);
        assert languageInfo != null;
        TextDocumentSurrogate surrogateOfTestFile = surrogateMap.getOrCreateSurrogate(runScriptUri, languageInfo);
        return surrogateOfTestFile;

    }

    private EvaluationResult evalInGlobalScope(String langId, Node nearestNode) {
        SourceSection section = nearestNode.getSourceSection();
        if (section == null || !section.isAvailable()) {
            return EvaluationResult.createUnknownExecutionTarget();
        }

        try {
            CallTarget callTarget = env.parse(
                    Source.newBuilder(langId, section.getCharacters(), "eval in global scope").cached(false).build());
            Object result = callTarget.call();
            return EvaluationResult.createResult(result);
        } catch (Exception e) {
            return EvaluationResult.createError(e);
        }
    }
}
