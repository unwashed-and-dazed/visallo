package org.visallo.web.routes.edge;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.vertexium.Graph;
import org.visallo.core.model.search.EdgeSearchRunner;
import org.visallo.core.model.search.VertexiumObjectSearchRunnerBase;
import org.visallo.core.model.search.SearchRepository;
import org.visallo.web.routes.vertex.VertexiumObjectSearchBase;

@Singleton
public class EdgeSearch extends VertexiumObjectSearchBase implements ParameterizedHandler {
    @Inject
    public EdgeSearch(Graph graph, SearchRepository searchRepository) {
        super(graph, (VertexiumObjectSearchRunnerBase) searchRepository.findSearchRunnerByUri(EdgeSearchRunner.URI));
    }
}
