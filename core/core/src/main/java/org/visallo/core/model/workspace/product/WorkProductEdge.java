package org.visallo.core.model.workspace.product;

import org.visallo.web.clientapi.model.ClientApiObject;

public class WorkProductEdge implements ClientApiObject {
    private String edgeId;
    private String label;
    private String outVertexId;
    private String inVertexId;
    private boolean unauthorized;

    public void setEdgeId(String edgeId) {
        this.edgeId = edgeId;
    }

    public String getEdgeId() {
        return edgeId;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public void setOutVertexId(String outVertexId) {
        this.outVertexId = outVertexId;
    }

    public String getOutVertexId() {
        return outVertexId;
    }

    public void setInVertexId(String inVertexId) {
        this.inVertexId = inVertexId;
    }

    public String getInVertexId() {
        return inVertexId;
    }

    public void setUnauthorized(boolean unauthorized) {
        this.unauthorized = unauthorized;
    }

    public boolean isUnauthorized() {
        return unauthorized;
    }
}
