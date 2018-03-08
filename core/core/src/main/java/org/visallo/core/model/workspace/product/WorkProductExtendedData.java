package org.visallo.core.model.workspace.product;

import org.visallo.web.clientapi.model.ClientApiObject;

import java.util.Map;

public class WorkProductExtendedData implements ClientApiObject {
    private Map<String, ? extends WorkProductVertex> vertices;
    private Map<String, ? extends WorkProductEdge> edges;

    public void setVertices(Map<String, ? extends WorkProductVertex> vertices) {
        this.vertices = vertices;
    }

    public Map<String, ? extends WorkProductVertex> getVertices() {
        return vertices;
    }

    public void setEdges(Map<String, ? extends WorkProductEdge> edges) {
        this.edges = edges;
    }

    public Map<String, ? extends WorkProductEdge> getEdges() {
        return edges;
    }
}
