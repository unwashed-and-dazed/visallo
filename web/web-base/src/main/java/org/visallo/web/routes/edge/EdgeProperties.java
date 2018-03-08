package org.visallo.web.routes.edge;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.vertexium.*;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.web.clientapi.model.ClientApiEdge;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

@Singleton
public class EdgeProperties implements ParameterizedHandler {
    private final Graph graph;

    @Inject
    public EdgeProperties(final Graph graph) {
        this.graph = graph;
    }

    @Handle
    public ClientApiEdge handle(
            @Required(name = "graphEdgeId") String graphEdgeId,
            @ActiveWorkspaceId String workspaceId,
            Authorizations authorizations
    ) throws Exception {
        Edge edge = graph.getEdge(graphEdgeId, authorizations);
        if (edge == null) {
            throw new VisalloResourceNotFoundException("Could not find edge: " + graphEdgeId);
        }

        Vertex outVertex = edge.getVertex(Direction.OUT, authorizations);
        if (outVertex == null) {
            throw new VisalloResourceNotFoundException("Could not find outVertex: " + edge.getVertexId(Direction.OUT));
        }

        Vertex inVertex = edge.getVertex(Direction.IN, authorizations);
        if (inVertex == null) {
            throw new VisalloResourceNotFoundException("Could not find inVertex: " + edge.getVertexId(Direction.IN));
        }

        return ClientApiConverter.toClientApiEdgeWithVertexData(edge, outVertex, inVertex, workspaceId, authorizations);
    }
}
