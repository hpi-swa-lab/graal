package org.graalvm.tools.lsp.arguments;

import com.oracle.truffle.tools.utils.json.JSONObject;
import java.net.URI;
import java.util.Objects;

public class DocumentUriArgument {
    final JSONObject jsonData;

    public DocumentUriArgument(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    public URI getDocumentUri() {
        final Object json = jsonData.opt("documentUri");

        if (json == null) {
            return null;
        }

        return (URI) json;
    }

    public DocumentUriArgument setDocumentUri(URI documentUri) {
        if (documentUri != null) {
            jsonData.put("documentUri", documentUri);
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

        DocumentUriArgument other = (DocumentUriArgument) obj;
        return Objects.equals(this.getDocumentUri(), other.getDocumentUri());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.getDocumentUri());
        return hash;
    }

    public static DocumentUriArgument create(URI uri) {
        final JSONObject json = new JSONObject();
        json.put("documentUri", uri);
        return new DocumentUriArgument(json);
    }

    public JSONObject getJsonData() {
        return jsonData;
    }
}
