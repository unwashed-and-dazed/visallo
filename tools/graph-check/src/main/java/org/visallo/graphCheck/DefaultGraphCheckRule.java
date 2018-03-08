package org.visallo.graphCheck;

import org.vertexium.*;

public class DefaultGraphCheckRule implements GraphCheckRule {
    @Override
    public void visitElement(GraphCheckContext ctx, Element element) {

    }

    @Override
    public void visitVertex(GraphCheckContext ctx, Vertex vertex) {

    }

    @Override
    public void visitEdge(GraphCheckContext ctx, Edge edge) {

    }

    @Override
    public void visitProperty(GraphCheckContext ctx, Element element, Property property) {

    }

    @Override
    public void visitExtendedDataRow(GraphCheckContext ctx, Element element, String tableName, ExtendedDataRow row) {

    }

    @Override
    public void visitProperty(GraphCheckContext ctx, Element element, String tableName, ExtendedDataRow row, Property property) {

    }

    protected void checkElementHasProperty(GraphCheckContext ctx, Element element, String propteryName) {
        Property property = element.getProperty(propteryName);
        if (property == null) {
            ctx.reportError(this, element, "Missing \"%s\"", propteryName);
        }
    }

    protected void checkElementDoesNotHaveProperty(GraphCheckContext ctx, Element element, String propteryName) {
        Property property = element.getProperty(propteryName);
        if (property != null) {
            ctx.reportError(this, element, "Should not have \"%s\"", propteryName);
        }
    }

    protected void checkPropertyHasMetadata(GraphCheckContext ctx, Element element, Property property, String metadataKey) {
        Metadata metadata = property.getMetadata();
        Object metadataValue = metadata.getValue(metadataKey);
        if (metadataValue == null) {
            ctx.reportError(this, element, property, "Missing \"%s\"", metadataKey);
        }
    }
}
