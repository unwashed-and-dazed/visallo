package org.visallo.web.routes.edge;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.visallo.web.clientapi.model.ClientApiEdgesExistsResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Singleton
public class EdgeExists implements ParameterizedHandler {
    private final Graph graph;

    @Inject
    public EdgeExists(final Graph graph) {
        this.graph = graph;
    }

    @Handle
    public ClientApiEdgesExistsResponse handle(
            @Required(name = "edgeIds[]") String[] edgeIdsParameter,
            Authorizations authorizations
    ) throws Exception {
        List<String> edgeIds = Arrays.asList(edgeIdsParameter);
        Map<String, Boolean> graphEdges = graph.doEdgesExist(edgeIds, authorizations);
        ClientApiEdgesExistsResponse result = new ClientApiEdgesExistsResponse();
        result.getExists().putAll(graphEdges);
        return result;
    }
}
