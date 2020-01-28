package org.graalvm.tools.lsp.server.types;

import com.oracle.truffle.tools.utils.json.JSONObject;

public class Decoration {
    final JSONObject jsonData;

    Decoration(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    public Range getRange() {
        return new Range(jsonData.getJSONObject("range"));
    }

    public void setRange(Range range) {
        jsonData.put("range", range.jsonData);
    }

    public String getDecorationText() {
        return jsonData.getString("decorationText");
    }

    public Decoration setDecorationText(String decorationText) {
        jsonData.put("decorationText", decorationText);
        return this;
    }

    public String getType() {
        return jsonData.getString("type");
    }

    public Decoration setType(String type) {
        jsonData.put("type", type);
        return this;
    }

    public static Decoration create(Range range, String decorationText, String type) {
        final JSONObject json = new JSONObject();
        json.put("range", range.jsonData);
        json.put("decorationText", decorationText);
        json.put("type", type);
        return new Decoration(json);
    }
}
