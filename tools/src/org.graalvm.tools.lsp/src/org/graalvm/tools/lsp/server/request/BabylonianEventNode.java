package org.graalvm.tools.lsp.server.request;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import org.graalvm.tools.lsp.definitions.AssertionDefinition;
import org.graalvm.tools.lsp.definitions.ExampleDefinition;
import org.graalvm.tools.lsp.definitions.ProbeDefinition;
import org.graalvm.tools.lsp.parsing.SourceToBabylonParser;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class BabylonianEventNode extends ExecutionEventNode {
    private static final String EXPRESSION_START = "expression=\"";

    @Child private ExecutableNode inlineExecutionNode;

    private final Env env;
    private final ExampleDefinition example;
    private final EventContext context;

    public BabylonianEventNode(Env env, ExampleDefinition example, EventContext context) {
        this.env = env;
        this.example = example;
        this.context = context;
    }

    @Override
    public void onReturnValue(VirtualFrame frame, Object result) {
        SourceSection sourceSection = context.getInstrumentedSourceSection();
        String uri = sourceSection.getSource().getName();

        if (result == null) {
            return;
        }

        // TODO Find a better way of enabling cross-file probing
        if (!uri.equals(example.getUri())) {
            uri = example.getUri().substring(0, example.getUri().lastIndexOf("/") + 1) + uri;
        }

        /* start with previous line. */
        int currentLineNumber = sourceSection.getStartLine() - 1;
        while (currentLineNumber >= 1) {
            String line = sourceSection.getSource().getCharacters(currentLineNumber).toString().trim();
            if (!findProbeOrAssertion(frame, result, sourceSection, uri, currentLineNumber, line)) {
                break; // stop searching for more probes and assertions
            }
            currentLineNumber--;
        }
    }

    private boolean findProbeOrAssertion(VirtualFrame frame, Object result, SourceSection sourceSection, String uri, int lineIndex, String line) {
        boolean acceptAll = example.getProbeMode() == ExampleDefinition.ProbeMode.ALL;
        boolean acceptDefault = example.getProbeMode() == ExampleDefinition.ProbeMode.DEFAULT;
        if (acceptAll || (acceptDefault && line.contains("<Probe"))) {
            Object probedValue = result;
            int probeLineIndex = lineIndex;
            if (line.contains(EXPRESSION_START)) {
                int beginIndex = line.indexOf(EXPRESSION_START) + EXPRESSION_START.length();
                // Extract expression
                String expression = line.substring(beginIndex, line.indexOf("\" ", beginIndex));
                Source source = Source.newBuilder(sourceSection.getSource().getLanguage(), expression, "<probe>").build();
                try {
                    probedValue = getInlineExecutionNode(frame, source).execute(frame);
                } catch (Exception e) {
                    probedValue = e.getMessage();
                }
            } else {
                probeLineIndex++; // Place probe on next line
            }
            ProbeDefinition probe = new ProbeDefinition(probeLineIndex);
            example.getProbes().add(probe);
            probe.setResult(probedValue);
            probe.setUri(uri);
            return true;
        } else if (acceptDefault && line.contains("<Assertion")) {
            int beginIndex = line.indexOf("<Assertion");
            String assertionContent = line.substring(beginIndex, line.indexOf("/>", beginIndex));
            Matcher m = SourceToBabylonParser.keyValueExtractionPattern.matcher(assertionContent);
            Map<String, String> assertionKeyValues = new HashMap<>();
            while (m.find()) {
                assertionKeyValues.put(m.group(1), m.group(2));
            }
            if (example.getExampleName().equals(assertionKeyValues.getOrDefault("example", ""))) {
                Object actualResult;
                Object expectedValue;
                if (assertionKeyValues.containsKey("expected")) {
                    actualResult = result;
                    expectedValue = SourceToBabylonParser.convertExpectedValueType(assertionKeyValues.get("expected"));
                } else if (assertionKeyValues.containsKey("expression")) {
                    expectedValue = true;
                    Source source = Source.newBuilder(sourceSection.getSource().getLanguage(), assertionKeyValues.get("expression"), "<assertion>").build();
                    try {
                        actualResult = getInlineExecutionNode(frame, source).execute(frame);
                    } catch (Exception e) {
                        // Hack: Show error as probe
                        ProbeDefinition probe = new ProbeDefinition(lineIndex);
                        example.getProbes().add(probe);
                        probe.setResult(e.getMessage());
                        probe.setUri(uri);
                        return true;
                    }
                } else {
                    return false; // neither value nor expression found
                }
                AssertionDefinition assertion = new AssertionDefinition(lineIndex, expectedValue);
                example.getAssertions().add(assertion);
                assertion.setResult(actualResult);
                assertion.setUri(uri);
                return true;
            }
        }
        return false;
    }

    private ExecutableNode getInlineExecutionNode(VirtualFrame frame, Source source) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        ExecutableNode newNode = env.parseInline(source, context.getInstrumentedNode(), frame.materialize());
        if (inlineExecutionNode == null) {
            inlineExecutionNode = insert(newNode);
        } else {
            inlineExecutionNode.replace(newNode);
        }
        notifyInserted(inlineExecutionNode); /* this might break, ask chumer */
        return inlineExecutionNode;
    }

}
