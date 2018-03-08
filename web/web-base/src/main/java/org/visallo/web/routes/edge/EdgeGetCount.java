package org.visallo.web.routes.edge;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.visallo.web.clientapi.model.ClientApiVertexCount;

@Singleton
public class EdgeGetCount implements ParameterizedHandler {
    private final Graph graph;

    @Inject
    public EdgeGetCount(Graph graph) {
        this.graph = graph;
    }

    @Handle
    public ClientApiVertexCount handle(Authorizations authorizations) throws Exception {
        long vertexCount = graph.getEdgeCount(authorizations);
        return new ClientApiVertexCount(vertexCount);
    }
}
