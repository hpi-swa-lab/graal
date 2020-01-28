package org.graalvm.tools.lsp.definitions;

public class LanguageAgnosticFunctionArgumentDefinition {
    private String name;

    // A JSON representation of the argument's default value
    private String defaultValue;

    public LanguageAgnosticFunctionArgumentDefinition(String name, String defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
