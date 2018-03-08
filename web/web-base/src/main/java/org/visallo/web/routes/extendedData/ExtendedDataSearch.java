package org.visallo.web.routes.extendedData;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.vertexium.Graph;
import org.visallo.core.model.search.VertexiumObjectSearchRunnerBase;
import org.visallo.core.model.search.ExtendedDataSearchRunner;
import org.visallo.core.model.search.SearchRepository;
import org.visallo.web.routes.vertex.VertexiumObjectSearchBase;

@Singleton
public class ExtendedDataSearch extends VertexiumObjectSearchBase implements ParameterizedHandler {
    @Inject
    public ExtendedDataSearch(Graph graph, SearchRepository searchRepository) {
        super(graph, (VertexiumObjectSearchRunnerBase) searchRepository.findSearchRunnerByUri(ExtendedDataSearchRunner.URI));
    }
}
