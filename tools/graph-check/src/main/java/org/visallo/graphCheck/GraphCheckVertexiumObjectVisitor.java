package org.visallo.graphCheck;

import org.vertexium.*;
import org.visallo.core.bootstrap.InjectHelper;
import org.visallo.core.config.Configuration;

import java.util.Collection;

public class GraphCheckVertexiumObjectVisitor implements GraphVisitor {
    private final GraphCheckContext ctx;
    private final Collection<GraphCheckRule> rules;

    public GraphCheckVertexiumObjectVisitor(GraphCheckContext ctx, Configuration configuration) {
        this.ctx = ctx;
        this.rules = InjectHelper.getInjectedServices(GraphCheckRule.class, configuration);
    }

    @Override
    public void visitElement(Element element) {
        for (GraphCheckRule rule : this.rules) {
            rule.visitElement(ctx, element);
        }
    }

    @Override
    public void visitVertex(Vertex vertex) {
        for (GraphCheckRule rule : this.rules) {
            rule.visitVertex(ctx, vertex);
        }
    }

    @Override
    public void visitEdge(Edge edge) {
        for (GraphCheckRule rule : this.rules) {
            rule.visitEdge(ctx, edge);
        }
    }

    @Override
    public void visitExtendedDataRow(Element element, String tableName, ExtendedDataRow row) {
        for (GraphCheckRule rule : this.rules) {
            rule.visitExtendedDataRow(ctx, element, tableName, row);
        }
    }

    @Override
    public void visitProperty(Element element, Property property) {
        for (GraphCheckRule rule : this.rules) {
            rule.visitProperty(ctx, element, property);
        }
    }

    @Override
    public void visitProperty(Element element, String tableName, ExtendedDataRow row, Property property) {
        for (GraphCheckRule rule : this.rules) {
            rule.visitProperty(ctx, element, tableName, row, property);
        }
    }
}
