package org.visallo.web.routes.vertex;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.vertexium.Vertex;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.web.clientapi.model.ClientApiElement;
import org.visallo.web.clientapi.model.ClientApiVertex;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

@Singleton
public class VertexProperties implements ParameterizedHandler {
    private final Graph graph;

    @Inject
    public VertexProperties(final Graph graph) {
        this.graph = graph;
    }

    @Handle
    public ClientApiVertex handle(
            @Required(name = "graphVertexId") String graphVertexId,
            @ActiveWorkspaceId String workspaceId,
            Authorizations authorizations
    ) throws Exception {
        Vertex vertex = graph.getVertex(graphVertexId, authorizations);
        if (vertex == null) {
            throw new VisalloResourceNotFoundException("Could not find vertex: " + graphVertexId);
        }
        return (ClientApiVertex) ClientApiConverter.toClientApi(vertex, workspaceId, authorizations);
    }
}
