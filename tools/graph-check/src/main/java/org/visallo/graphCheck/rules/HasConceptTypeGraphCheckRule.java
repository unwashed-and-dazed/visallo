package org.visallo.graphCheck.rules;

import org.vertexium.Edge;
import org.vertexium.Vertex;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.graphCheck.DefaultGraphCheckRule;
import org.visallo.graphCheck.GraphCheckContext;

public class HasConceptTypeGraphCheckRule extends DefaultGraphCheckRule {
    @Override
    public void visitVertex(GraphCheckContext ctx, Vertex vertex) {
        checkElementHasProperty(ctx, vertex, VisalloProperties.CONCEPT_TYPE.getPropertyName());
    }

    @Override
    public void visitEdge(GraphCheckContext ctx, Edge edge) {
        checkElementDoesNotHaveProperty(ctx, edge, VisalloProperties.CONCEPT_TYPE.getPropertyName());
    }
}
