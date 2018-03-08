package org.visallo.web.product.graph.model;

import org.visallo.core.model.workspace.product.UpdateProductEdgeOptions;
import org.visallo.web.clientapi.model.GraphPosition;

import java.util.ArrayList;
import java.util.List;

public class GraphUpdateProductEdgeOptions extends UpdateProductEdgeOptions {
    private String id;
    private List<String> children = new ArrayList<>();
    private String parent;
    private GraphPosition pos;

    public String getId() {
        return id;
    }

    public GraphUpdateProductEdgeOptions setId(String id) {
        this.id = id;
        return this;
    }

    public List<String> getChildren() {
        return children;
    }

    public String getParent() {
        return parent;
    }

    public GraphUpdateProductEdgeOptions setParent(String parent) {
        this.parent = parent;
        return this;
    }

    public GraphUpdateProductEdgeOptions setPos(GraphPosition pos) {
        this.pos = pos;
        return this;
    }

    public GraphPosition getPos() {
        return pos;
    }

    public boolean hasChildren() {
        return children != null && children.size() > 0;
    }
}
