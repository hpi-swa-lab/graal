package org.graalvm.tools.lsp.parsing;

import org.graalvm.tools.lsp.definitions.ExampleDefinition;
import org.graalvm.tools.lsp.definitions.LanguageAgnosticFunctionArgumentDefinition;
import org.graalvm.tools.lsp.definitions.LanguageAgnosticFunctionDeclarationDefinition;
import org.graalvm.tools.lsp.definitions.ProbeDefinition;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceToBabylonParser {
    private static final String exampleAnnotationPattern = "<Example (?<paramString>.*(?<nameAssign>:name=\"(?<name>[a-zA-Z0-9_]*)\") (?<probeAssign>:probe-mode=\"(?<mode>[a-zA-Z0-9_]*)\")?.*)\\/>";
    private static final String exampleAnnotationPatternPrefix = "<Example .*:name=\"";

    String annotatedSource;
    String exampleInvocationCode;

    String uri;
    List<ExampleDefinition> examples = new LinkedList<>();
    List<LanguageAgnosticFunctionDeclarationDefinition> functionsInSource;

    public SourceToBabylonParser(String source,
                                 List<LanguageAgnosticFunctionDeclarationDefinition> functionsInSource,
                                 String uri) {
        this.annotatedSource = source;
        this.functionsInSource = functionsInSource;
        this.uri = uri;
    }

    public List<ExampleDefinition> parseExamples() {
        Map<String, List<Object>> exampleNamesToLineNumberAndParameterStrings = new LinkedHashMap<>();

        this.readExamplesFromSource().forEach((example, lineNumberAndParameterStrings) -> {
            String probeMode = example[1];
            int lineNumber = (Integer) lineNumberAndParameterStrings.keySet().toArray()[0];
            String parameterStrings = lineNumberAndParameterStrings.get(lineNumber);
            Map<String, Object> exampleParameters = new HashMap<>();
            for (String substring : parameterStrings.split(" ")) {
                if (!substring.equals("")) {
                    exampleParameters.put(substring.split("=")[0], substring.split("=")[1].replace(",", ""));
                }
            }
            this.exampleInvocationCode = this.getExampleInvocationCode(lineNumber, exampleParameters, this.functionsInSource);
            List<ProbeDefinition> probes = this.getProbesForExample(lineNumber, this.getFunctionEndLineNumber(lineNumber), probeMode);
            this.examples.add(new ExampleDefinition(example[0],
                    lineNumber,
                    this.exampleInvocationCode,
                    probes,
                    this.getExampleDefinitionLine(example[0]),
                    this.getExampleDefinitionEndColumn(example[0]),
                    this.uri, probeMode));
        });

        return this.examples;
    }

    private Map<String[], Map<Integer, String>> readExamplesFromSource() {
        Map<String[], Map<Integer, String>> exampleNamesToLineNumberAndParameterStrings = new HashMap<>();
        Matcher m = Pattern.compile(exampleAnnotationPattern).matcher(this.annotatedSource);
        while (m.find()) {
            Integer lineNumberOfFunctionDefForExample = this.getLineNumberOfFunctionDefForExample(m.group());
            Map<Integer, String> lineNumberToParameterStrings = new HashMap<>();
            String probeMode = m.group("mode") != null ? m.group("mode") : "default";
            String exampleNameAssignment = m.group("nameAssign");
            String probeModeAssignment = m.group("probeAssign");
            String parameterString = m.group("paramString").replace(exampleNameAssignment, "").trim();
            if (probeModeAssignment != null) {
                parameterString = parameterString.replace(probeModeAssignment, "").trim();
            }
            lineNumberToParameterStrings.put(lineNumberOfFunctionDefForExample, parameterString);
            String exampleName = m.group("name");
            String[] example = new String[]{exampleName, probeMode};
            exampleNamesToLineNumberAndParameterStrings.put(example, lineNumberToParameterStrings);

            lineNumberToParameterStrings.put(lineNumberOfFunctionDefForExample, parameterString);
        }
        return exampleNamesToLineNumberAndParameterStrings;
    }

    private Integer getFunctionEndLineNumber(Integer lineNumberStart) {

        for (LanguageAgnosticFunctionDeclarationDefinition functionDef : this.functionsInSource) {
            if (functionDef.getStartLine() == lineNumberStart) {
                return functionDef.getEndLine();
            }
        }
        return -1;
    }

    private Integer getLineNumberOfFunctionDefForExample(String fullExampleString) {
        String[] lines = this.annotatedSource.split("\n");
        boolean found = false;

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(fullExampleString)) {
                // the example was found, the next non-comment line is the function beginning
                found = true;
            }
            if (found) {
                // the last line of the example comment
                if (lines[i].contains("*/")) {
                    return i + 2;
                }
            }
        }
        return -1;
    }

    public int getExampleDefinitionLine(String exampleName) {
        String pattern = exampleAnnotationPatternPrefix + exampleName;
        Pattern m = Pattern.compile(pattern);

        int lineNumber = 1;
        for (String line : this.annotatedSource.split("\n")) {
            if (m.matcher(line).find())
                return lineNumber;
            lineNumber++;
        }

        return -1;
    }

    public int getExampleDefinitionEndColumn(String exampleName) {
        String pattern = exampleAnnotationPatternPrefix + exampleName;
        Pattern m = Pattern.compile(pattern);

        for (String line : this.annotatedSource.split("\n")) {
            if (m.matcher(line).find())
                return line.length();
        }

        return -1;
    }

    public String getExampleInvocationCode(Integer lineNumber, Map<String, Object> exampleParameters,
                                           List<LanguageAgnosticFunctionDeclarationDefinition> functionsInSource) {
        String functionNameForExample = "";
        List<LanguageAgnosticFunctionArgumentDefinition> functionArguments = new ArrayList<>();
        List<String> functionArgumentNames = new ArrayList<>();

        for (LanguageAgnosticFunctionDeclarationDefinition functionDef : functionsInSource) {
            if (functionDef.getStartLine() == lineNumber) {
                functionNameForExample = functionDef.getName();
                functionArguments = functionDef.getArguments();
                for (LanguageAgnosticFunctionArgumentDefinition argumentDefinition : functionArguments) {
                    functionArgumentNames.add(argumentDefinition.getName());
                }
                break;
            }
        }

        List<String> argumentValues = new ArrayList<>();
        functionArgumentNames.forEach(functionArgumentName -> {
            Object argumentValue = exampleParameters.get(functionArgumentName);
            argumentValues.add(argumentValue.toString());
        });

        return functionNameForExample + "(" + String.join(", ", argumentValues) + ")";
    }

    private Boolean needsExplicitProbe(String probeMode, String[] lines, Integer at) {
        return ((!probeMode.equals("all") && lines[at].trim().equals("// <Probe />")));
    }

    private List<ProbeDefinition> getProbesForExample(Integer functionStart, Integer functionEnd, String probeMode) {
        String[] lines = this.annotatedSource.split("\n");
        ArrayList<ProbeDefinition> probes = new ArrayList<>();

        for (int i = functionStart - 1; i < functionEnd; i++) {
            if (lines[i].contains("<Probe />")) {
                probes.add(new ProbeDefinition(i + 2));
            }
        }
        return probes;
    }

}