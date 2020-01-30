package org.graalvm.tools.lsp.server.types;

import com.oracle.truffle.tools.utils.json.JSONObject;

public class Decoration {
    public static final String PROBE_DECORATION_TYPE = "PROBE_DECORATION";
    public static final String ASSERTION_DECORATION_TYPE = "ASSERTION_DECORATION";
    public static final String EXAMPLE_DECORATION_TYPE = "EXAMPLE_DECORATION";

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

    public String getDecorationType() {
        return jsonData.getString("decorationType");
    }

    public Decoration setDecorationType(String decorationType) {
        jsonData.put("decorationType", decorationType);
        return this;
    }

    public static Decoration create(Range range, String decorationText, String decorationType) {
        final JSONObject json = new JSONObject();
        json.put("range", range.jsonData);
        json.put("decorationText", decorationText);
        json.put("decorationType", decorationType);
        return new Decoration(json);
    }
}
