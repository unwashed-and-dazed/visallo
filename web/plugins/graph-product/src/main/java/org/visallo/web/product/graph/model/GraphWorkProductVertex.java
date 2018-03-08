package org.visallo.web.product.graph.model;

import org.visallo.core.model.workspace.product.WorkProductVertex;
import org.visallo.web.clientapi.model.GraphPosition;

import java.util.List;

public class GraphWorkProductVertex extends WorkProductVertex {
    private String parent;
    private GraphPosition pos;
    private List<String> children;

    public void setChildren(List<String> children) {
        this.children = children;
    }

    public List<String> getChildren() {
        return children;
    }

    public void setPos(GraphPosition pos) {
        this.pos = pos;
    }

    public GraphPosition getPos() {
        return pos;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    public String getParent() {
        return parent;
    }
}
