package org.visallo.web.routes.vertex;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.visallo.web.VisalloResponse;
import org.visallo.web.clientapi.model.ClientApiVerticesExistsResponse;

import java.util.Map;

@Singleton
public class VertexExists implements ParameterizedHandler {
    private final Graph graph;

    @Inject
    public VertexExists(final Graph graph) {
        this.graph = graph;
    }

    @Handle
    public ClientApiVerticesExistsResponse handle(
            @Required(name = "vertexIds[]") String[] vertexIds,
            VisalloResponse response,
            Authorizations authorizations
    ) throws Exception {
        Map<String, Boolean> graphVertices = graph.doVerticesExist(Lists.newArrayList(vertexIds), authorizations);
        ClientApiVerticesExistsResponse result = new ClientApiVerticesExistsResponse();
        result.getExists().putAll(graphVertices);
        return result;
    }
}
