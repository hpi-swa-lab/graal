package org.graalvm.tools.lsp.arguments;

import com.oracle.truffle.tools.utils.json.JSONObject;

import java.util.Map;
import java.util.Objects;


public class CommandExpectingUserInputArgument {
    final JSONObject jsonData;

    public CommandExpectingUserInputArgument(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    public Map getInputMapping() {
        final Object json = jsonData.opt("inputMapping");

        if (json == null) {
            return null;
        }

        return (Map) json;
    }

    public CommandExpectingUserInputArgument setInputMapping(Map inputMapping) {
        if (inputMapping != null) {
            jsonData.put("inputMapping", inputMapping);
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

        CommandExpectingUserInputArgument other = (CommandExpectingUserInputArgument) obj;
        return Objects.equals(this.getInputMapping(), other.getInputMapping());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.getInputMapping());
        return hash;
    }

    public static CodeLensPositionArgument create(Map inputMapping) {
        final JSONObject json = new JSONObject();
        json.put("inputMapping", inputMapping);
        return new CodeLensPositionArgument(json);
    }

    public JSONObject getJsonData() {
        return jsonData;
    }
}
