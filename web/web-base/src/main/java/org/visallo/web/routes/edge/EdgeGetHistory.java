package org.visallo.web.routes.edge;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Optional;
import org.visallo.webster.annotations.Required;
import org.vertexium.Authorizations;
import org.vertexium.Edge;
import org.vertexium.Graph;
import org.vertexium.HistoricalPropertyValue;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.web.clientapi.model.ClientApiHistoricalPropertyResults;

import java.util.Locale;
import java.util.ResourceBundle;

@Singleton
public class EdgeGetHistory implements ParameterizedHandler {
    private Graph graph;

    @Inject
    public EdgeGetHistory(Graph graph) {
        this.graph = graph;
    }

    @Handle
    public ClientApiHistoricalPropertyResults handle(
            @Required(name = "graphEdgeId") String graphEdgeId,
            @Optional(name = "startTime") Long startTime,
            @Optional(name = "endTime") Long endTime,
            @Optional(name = "withVisibility") Boolean withVisibility,
            Locale locale,
            ResourceBundle resourceBundle,
            Authorizations authorizations
    ) throws Exception {
        Edge edge = graph.getEdge(graphEdgeId, authorizations);
        if (edge == null) {
            throw new VisalloResourceNotFoundException(String.format("edge %s not found", graphEdgeId));
        }

        Iterable<HistoricalPropertyValue> historicalPropertyValues = edge.getHistoricalPropertyValues(
                startTime,
                endTime,
                authorizations
        );
        return ClientApiConverter.toClientApi(historicalPropertyValues, locale, resourceBundle, withVisibility);
    }
}
