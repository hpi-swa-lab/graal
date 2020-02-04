package org.graalvm.tools.lsp.definitions;

public class AssertionDefinition extends ProbeDefinition {
    private Object expectedValue;

    public AssertionDefinition(int line, Object expectedValue) {
        super(line);
        this.expectedValue = expectedValue;
    }

    public boolean isAssertionTrue() {
        return this.expectedValue.equals(this.getResult());
    }
}
