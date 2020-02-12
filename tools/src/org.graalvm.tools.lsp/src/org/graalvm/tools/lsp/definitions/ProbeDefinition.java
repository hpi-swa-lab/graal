package org.graalvm.tools.lsp.definitions;

public class ProbeDefinition {
    private String uri;
    private int line;
    private Object result;

    public ProbeDefinition(int line) {
        this.line = line;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public int getLine() {
        return line;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }
}
