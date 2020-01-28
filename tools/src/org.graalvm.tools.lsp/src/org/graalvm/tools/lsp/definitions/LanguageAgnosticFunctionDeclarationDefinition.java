package org.graalvm.tools.lsp.definitions;

import java.util.List;

public class LanguageAgnosticFunctionDeclarationDefinition extends LanguageAgnosticSymbolDefinition {
    private String name;
    private List<LanguageAgnosticFunctionArgumentDefinition> arguments;
    private int startLine;
    private int endLine;

    public LanguageAgnosticFunctionDeclarationDefinition(String name, int startLine, int endLine, List<LanguageAgnosticFunctionArgumentDefinition> arguments) {
        this.name = name;
        this.arguments = arguments;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    @Override
    public String getType() {
        return "function";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<LanguageAgnosticFunctionArgumentDefinition> getArguments() {
        return arguments;
    }

    public void setArguments(List<LanguageAgnosticFunctionArgumentDefinition> arguments) {
        this.arguments = arguments;
    }

    public void addArgument(LanguageAgnosticFunctionArgumentDefinition argument) {
        this.arguments.add(argument);
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public Integer getEndLine() {
        return endLine;
    }
}
