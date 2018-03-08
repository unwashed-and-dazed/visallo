package org.visallo.graphCheck;

import org.vertexium.*;

public interface GraphCheckRule {
    void visitElement(GraphCheckContext ctx, Element element);

    void visitVertex(GraphCheckContext ctx, Vertex vertex);

    void visitEdge(GraphCheckContext ctx, Edge edge);

    void visitProperty(GraphCheckContext ctx, Element element, Property property);

    void visitExtendedDataRow(GraphCheckContext ctx, Element element, String tableName, ExtendedDataRow row);

    void visitProperty(GraphCheckContext ctx, Element element, String tableName, ExtendedDataRow row, Property property);
}
