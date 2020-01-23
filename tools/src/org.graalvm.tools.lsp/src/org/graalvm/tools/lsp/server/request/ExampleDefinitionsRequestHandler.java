package org.graalvm.tools.lsp.server.request;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.tools.lsp.definitions.ExampleDefinition;
import org.graalvm.tools.lsp.definitions.LanguageAgnosticFunctionDeclarationDefinition;
import org.graalvm.tools.lsp.parsing.SourceToBabylonParser;
import org.graalvm.tools.lsp.server.ContextAwareExecutor;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExampleDefinitionsRequestHandler extends AbstractRequestHandler {
    public ExampleDefinitionsRequestHandler(TruffleInstrument.Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor contextAwareExecutor) {
        super(env, surrogateMap, contextAwareExecutor);
    }

    public List<ExampleDefinition> exampleDefinitionsWithEnteredContext(URI uri, String sourceCode) {
        Map<SourceSection, LanguageAgnosticFunctionDeclarationDefinition> functionDeclarations = surrogateMap.get(uri).getFunctionDeclarationDefinitionMap();
        List<LanguageAgnosticFunctionDeclarationDefinition> functions = new ArrayList<>(functionDeclarations.values());

        // use parser for one source file
        final SourceToBabylonParser parser = new SourceToBabylonParser(sourceCode, functions, uri.toString());
        return parser.parseExamples();
    }
}
