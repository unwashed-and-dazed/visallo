package org.visallo.web.routes.extendedData;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Optional;
import org.visallo.webster.annotations.Required;
import org.vertexium.Authorizations;
import org.vertexium.ElementType;
import org.vertexium.ExtendedDataRow;
import org.vertexium.Graph;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.web.clientapi.model.ClientApiExtendedDataGetResponse;

@Singleton
public class ExtendedDataGet implements ParameterizedHandler {
    private final Graph graph;

    @Inject
    public ExtendedDataGet(Graph graph) {
        this.graph = graph;
    }

    @Handle
    public ClientApiExtendedDataGetResponse handle(
            @Required(name = "elementType") ElementType elementType,
            @Required(name = "elementId") String elementId,
            @Required(name = "tableName") String tableName,
            Authorizations authorizations
    ) throws Exception {
        Iterable<ExtendedDataRow> rows = graph.getExtendedData(elementType, elementId, tableName, authorizations);
        return new ClientApiExtendedDataGetResponse(ClientApiConverter.toClientApiExtendedDataRows(rows));
    }
}
