package org.visallo.web.product.map;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.vertexium.*;
import org.visallo.core.model.graph.ElementUpdateContext;
import org.visallo.core.model.graph.GraphUpdateContext;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.model.workspace.product.UpdateProductEdgeOptions;
import org.visallo.core.model.workspace.product.WorkProductEdge;
import org.visallo.core.model.workspace.product.WorkProductServiceHasElementsBase;
import org.visallo.core.model.workspace.product.WorkProductVertex;

import java.util.Map;
import java.util.Set;

@Singleton
public class MapWorkProductService extends WorkProductServiceHasElementsBase<WorkProductVertex, WorkProductEdge> {
    public static final String KIND = "org.visallo.web.product.map.MapWorkProduct";

    @Inject
    public MapWorkProductService(
            AuthorizationRepository authorizationRepository
    ) {
        super(authorizationRepository);
    }

    @Override
    protected WorkProductEdge createWorkProductEdge() {
        return new WorkProductEdge();
    }

    @Override
    protected WorkProductVertex createWorkProductVertex() {
        return new WorkProductVertex();
    }

    @Override
    protected void populateCustomProductVertexWithWorkspaceEdge(Edge propertyVertexEdge, WorkProductVertex vertex) {

    }

    @Override
    protected void updateProductEdge(ElementUpdateContext<Edge> elemCtx, UpdateProductEdgeOptions update, Visibility visibility) {
    }


    public void updateVertices(
            GraphUpdateContext ctx,
            Vertex productVertex,
            Map<String, UpdateProductEdgeOptions> updateVertices,
            Visibility visibility
    ) {
        if (updateVertices != null) {
            @SuppressWarnings("unchecked")
            Set<String> vertexIds = updateVertices.keySet();
            for (String id : vertexIds) {
                UpdateProductEdgeOptions updateData = updateVertices.get(id);

                addOrUpdateProductEdgeToEntity(ctx, productVertex, id, updateData, WorkspaceRepository.VISIBILITY.getVisibility());
            }
        }
    }

    public void removeVertices(
            GraphUpdateContext ctx,
            Vertex productVertex,
            String[] removeVertices,
            Authorizations authorizations
    ) {
        if (removeVertices != null) {
            for (String id : removeVertices) {
                Edge productVertexEdge = ctx.getGraph().getEdge(
                        getEdgeId(productVertex.getId(), id),
                        FetchHint.NONE,
                        authorizations
                );

                if (productVertexEdge != null) {
                    ctx.getGraph().softDeleteEdge(productVertexEdge, authorizations);
                }
            }
        }
    }

    @Override
    public String getKind() {
        return KIND;
    }
}
