package org.visallo.core.model.workspace.product;

import org.visallo.web.clientapi.model.ClientApiObject;

public class GetExtendedDataParams implements ClientApiObject {
    private boolean includeVertices;
    private boolean includeEdges;

    public boolean isIncludeVertices() {
        return includeVertices;
    }

    public GetExtendedDataParams setIncludeVertices(boolean includeVertices) {
        this.includeVertices = includeVertices;
        return this;
    }

    public boolean isIncludeEdges() {
        return includeEdges;
    }

    public GetExtendedDataParams setIncludeEdges(boolean includeEdges) {
        this.includeEdges = includeEdges;
        return this;
    }
}
