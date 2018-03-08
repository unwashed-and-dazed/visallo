package org.visallo.web.structuredingest.core.routes;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.vertexium.Vertex;
import org.vertexium.property.StreamingPropertyValue;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.web.structuredingest.core.model.ClientApiAnalysis;
import org.visallo.web.structuredingest.core.model.StructuredIngestParser;
import org.visallo.web.structuredingest.core.util.StructuredIngestParserFactory;

import java.io.InputStream;
import java.util.List;

@Singleton
public class Analyze implements ParameterizedHandler {
    private final Graph graph;
    private final StructuredIngestParserFactory structuredIngestParserFactory;

    @Inject
    public Analyze(Graph graph, StructuredIngestParserFactory structuredIngestParserFactory) {
        this.graph = graph;
        this.structuredIngestParserFactory = structuredIngestParserFactory;
    }

    @Handle
    public ClientApiAnalysis handle(
            Authorizations authorizations,
            @Required(name = "graphVertexId") String graphVertexId
    ) throws Exception {
        Vertex vertex = graph.getVertex(graphVertexId, authorizations);
        if (vertex == null) {
            throw new VisalloResourceNotFoundException("Could not find vertex:" + graphVertexId);
        }

        StreamingPropertyValue rawPropertyValue = VisalloProperties.RAW.getPropertyValue(vertex);
        if (rawPropertyValue == null) {
            throw new VisalloResourceNotFoundException("Could not find raw property on vertex:" + graphVertexId);
        }

        List <String> mimeTypes = Lists.newArrayList(VisalloProperties.MIME_TYPE.getPropertyValues(vertex));
        for (String mimeType : mimeTypes) {
            StructuredIngestParser parser = structuredIngestParserFactory.getParser(mimeType);
            if (parser != null) {
                try (InputStream inputStream = rawPropertyValue.getInputStream()) {
                    return parser.analyze(inputStream);
                }
            }
        }

        return null;
    }
}
