package org.graalvm.tools.lsp.server.request;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.utils.json.JSONObject;
import org.graalvm.tools.lsp.arguments.CodeLensPositionArgument;
import org.graalvm.tools.lsp.arguments.CommandExpectingUserInputArgument;
import org.graalvm.tools.lsp.arguments.DocumentUriArgument;
import org.graalvm.tools.lsp.definitions.LanguageAgnosticFunctionDeclarationDefinition;
import org.graalvm.tools.lsp.server.ContextAwareExecutor;
import org.graalvm.tools.lsp.server.types.CodeLens;
import org.graalvm.tools.lsp.server.types.Command;
import org.graalvm.tools.lsp.server.types.Position;
import org.graalvm.tools.lsp.server.types.Range;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.graalvm.tools.lsp.server.LanguageServerImpl.ADD_EXAMPLE_COMMAND;

public class CodeLensRequestHandler extends AbstractRequestHandler {
    public CodeLensRequestHandler(TruffleInstrument.Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor contextAwareExecutor) {
        super(env, surrogateMap, contextAwareExecutor);
    }

    public List<? extends CodeLens> codeLensWithEnteredContext(URI uri) {
        Map<SourceSection, LanguageAgnosticFunctionDeclarationDefinition> functionDeclarations = surrogateMap.get(uri).getFunctionDeclarationDefinitionMap();
        ArrayList<LanguageAgnosticFunctionDeclarationDefinition> functions = new ArrayList<>(functionDeclarations.values());

        ArrayList<CodeLens> codeLenses = new ArrayList<>();

        for (LanguageAgnosticFunctionDeclarationDefinition function : functions) {
            List<JSONObject> arguments = new ArrayList<>();

            if (function.getArguments().size() > 0) {
                JSONObject expectedInput = new JSONObject();

                function.getArguments().forEach(argument -> expectedInput.put(argument.getName(), "any"));

                JSONObject expectedArg = new JSONObject();
                expectedArg.put("inputMapping", expectedInput);
                arguments.add(expectedArg);
            }

            JSONObject positionArg = new JSONObject();
            positionArg.put("startLine", function.getStartLine());
            arguments.add(positionArg);

            JSONObject documentUriArg = new JSONObject();
            positionArg.put("documentUri", uri);
            arguments.add(documentUriArg);

            Command commandAddExample = Command.create("Add Example", ADD_EXAMPLE_COMMAND);
            commandAddExample.setArguments(arguments);

            CodeLens codeLensAddExample = CodeLens.create(Range.create(
                    Position.create(function.getStartLine() - 1, 0),
                    Position.create(function.getStartLine() - 1, 0)
            ), null);
            codeLensAddExample.setCommand(commandAddExample);

            codeLenses.add(codeLensAddExample);
        }

        return codeLenses;
    }
}
