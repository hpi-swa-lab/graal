package org.graalvm.tools.lsp.definitions;

import java.util.ArrayList;
import java.util.List;

public class ExampleDefinition {
    private String exampleName;
    private int functionStartLine;
    private String functionName;
    private List<ProbeDefinition> probes;
    private List<AssertionDefinition> assertions;
    private Boolean probeAll;
    private Object exampleResult;
    private int exampleDefinitionLine;
    private int exampleDefinitionEndColumn;
    private String uri;

    public ExampleDefinition(String exampleName,
                             int functionStartLine,
                             String functionName,
                             int exampleDefinitionLine,
                             int exampleDefinitionEndColumn,
                             String uri, String probeMode) {
        this.exampleName = exampleName;
        this.functionStartLine = functionStartLine;
        this.functionName = functionName;
        this.probes = new ArrayList<>();
        this.assertions = new ArrayList<>();
        this.probeAll = (probeMode.equals("all"));
        this.exampleDefinitionLine = exampleDefinitionLine;
        this.exampleDefinitionEndColumn = exampleDefinitionEndColumn;
        this.uri = uri;
    }

    public String getExampleName() {
        return exampleName;
    }

    public int getFunctionStartLine() {
        return functionStartLine;
    }

    public String getFunctionName() {
        return functionName;
    }

    public List<ProbeDefinition> getProbes() {
        return this.probes;
    }

    public List<AssertionDefinition> getAssertions() {
        return this.assertions;
    }

    public Boolean getProbeAll() {
        return probeAll;
    }

    public Object getExampleResult() {
        return exampleResult;
    }

    public void setExampleResult(Object exampleResult) {
        this.exampleResult = exampleResult;
    }

    public int getExampleDefinitionLine() {
        return exampleDefinitionLine;
    }

    public int getExampleDefinitionEndColumn() {
        return exampleDefinitionEndColumn;
    }

    public String getUri() {
        return uri;
    }
}