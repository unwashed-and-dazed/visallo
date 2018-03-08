package org.visallo.web.routes.edge;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Optional;
import org.visallo.webster.annotations.Required;
import org.vertexium.*;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.web.clientapi.model.ClientApiHistoricalPropertyResults;

import java.util.Locale;
import java.util.ResourceBundle;

@Singleton
public class EdgeGetPropertyHistory implements ParameterizedHandler {
    private Graph graph;

    @Inject
    public EdgeGetPropertyHistory(final Graph graph) {
        this.graph = graph;
    }

    @Handle
    public ClientApiHistoricalPropertyResults handle(
            @Required(name = "graphEdgeId") String graphEdgeId,
            @Required(name = "propertyKey") String propertyKey,
            @Required(name = "propertyName") String propertyName,
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

        Property property = edge.getProperty(propertyKey, propertyName);
        if (property == null) {
            throw new VisalloResourceNotFoundException(String.format("property %s:%s not found on edge %s", propertyKey, propertyName, edge.getId()));
        }

        Iterable<HistoricalPropertyValue> historicalPropertyValues = edge.getHistoricalPropertyValues(
                property.getKey(),
                property.getName(),
                property.getVisibility(),
                startTime,
                endTime,
                authorizations
        );
        return ClientApiConverter.toClientApi(historicalPropertyValues, locale, resourceBundle, withVisibility);

    }
}
