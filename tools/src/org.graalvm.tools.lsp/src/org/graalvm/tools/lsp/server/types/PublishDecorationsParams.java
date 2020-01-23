package org.graalvm.tools.lsp.server.types;


import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decoration notifications are sent from the server to the client to signal probing results per file identified by its URI.
 */
public class PublishDecorationsParams {
    final JSONObject jsonData;

    PublishDecorationsParams(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * The URI for which diagnostic information is reported.
     */
    public String getUri() {
        return jsonData.getString("uri");
    }

    public PublishDecorationsParams setUri(String uri) {
        jsonData.put("uri", uri);
        return this;
    }

    /**
     * An array of diagnostic information items.
     */
    public List<Decoration> getDecorations() {
        final JSONArray json = jsonData.getJSONArray("decorations");
        final List<Decoration> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new Decoration(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public PublishDecorationsParams setDecorations(List<Decoration> decorations) {
        final JSONArray json = new JSONArray();
        for (Decoration decoration : decorations) {
            json.put(decoration.jsonData);
        }
        jsonData.put("decorations", json);
        return this;
    }

    public static PublishDecorationsParams create(String uri, List<Decoration> decorations) {
        final JSONObject json = new JSONObject();
        json.put("uri", uri);
        JSONArray diagnosticsJsonArr = new JSONArray();
        for (Decoration decoration : decorations) {
            diagnosticsJsonArr.put(decoration.jsonData);
        }
        json.put("decorations", diagnosticsJsonArr);
        return new PublishDecorationsParams(json);
    }
}
