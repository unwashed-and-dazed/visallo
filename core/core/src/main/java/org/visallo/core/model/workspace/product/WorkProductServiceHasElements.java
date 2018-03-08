package org.visallo.core.model.workspace.product;

import org.vertexium.*;
import org.visallo.core.model.graph.GraphUpdateContext;

public interface WorkProductServiceHasElements<TVertex extends WorkProductVertex, TEdge extends WorkProductEdge> {

    void cleanUpElements(Graph graph, Vertex productVertex, Authorizations authorizations);

    void populateProductVertexWithWorkspaceEdge(Edge propertyVertexEdge, TVertex vertex);

    TVertex populateProductVertexWithWorkspaceEdge(Edge propertyVertexEdge);

    GraphUpdateContext.UpdateFuture<Edge> addOrUpdateProductEdgeToEntity(GraphUpdateContext ctx, Vertex productVertex, String entityId, UpdateProductEdgeOptions options, Visibility visibility);

    GraphUpdateContext.UpdateFuture<Edge> addOrUpdateProductEdgeToEntity(GraphUpdateContext ctx, String edgeId, Vertex productVertex, String entityId, UpdateProductEdgeOptions options, Visibility visibility);

    /**
     * Add an entity that is hidden with workspace visibility that can still behave like a normal vertex in products with differences:
     * Can't be selected, searches, published
     */
    GraphUpdateContext.UpdateFuture<Edge> addOrUpdateProductEdgeToAncillaryEntity(GraphUpdateContext ctx, Vertex productVertex, String entityId, UpdateProductEdgeOptions options, Visibility visibility);
}
