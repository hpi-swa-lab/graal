package org.graalvm.tools.lsp.arguments;

import com.oracle.truffle.tools.utils.json.JSONObject;

import java.util.Objects;

public class CodeLensPositionArgument {
    final JSONObject jsonData;

    public CodeLensPositionArgument(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    public Integer getStartLine() {
        final Object json = jsonData.opt("startLine");

        if (json == null) {
            return null;
        }

        return (Integer) json;
    }

    public CodeLensPositionArgument setStartLine(Integer startLine) {
        if (startLine != null) {
            jsonData.put("startLine", startLine);
        }
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (this.getClass() != obj.getClass()) {
            return false;
        }

        CodeLensPositionArgument other = (CodeLensPositionArgument) obj;
        return Objects.equals(this.getStartLine(), other.getStartLine());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.getStartLine());
        return hash;
    }

    public static CodeLensPositionArgument create(Integer startLine) {
        final JSONObject json = new JSONObject();
        json.put("startLine", startLine);
        return new CodeLensPositionArgument(json);
    }

    public JSONObject getJsonData() {
        return jsonData;
    }
}

