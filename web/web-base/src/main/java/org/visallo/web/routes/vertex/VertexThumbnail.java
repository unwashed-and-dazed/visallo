package org.visallo.web.routes.vertex;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Optional;
import org.visallo.webster.annotations.Required;
import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.vertexium.Property;
import org.vertexium.Vertex;
import org.vertexium.property.StreamingPropertyValue;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.thumbnails.Thumbnail;
import org.visallo.core.model.thumbnails.ThumbnailRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.VisalloResponse;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

import java.io.InputStream;
import java.io.OutputStream;

@Singleton
public class VertexThumbnail implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(VertexThumbnail.class);

    private final ThumbnailRepository thumbnailRepository;
    private final Graph graph;

    @Inject
    public VertexThumbnail(
            final ThumbnailRepository thumbnailRepository,
            final Graph graph
    ) {
        this.thumbnailRepository = thumbnailRepository;
        this.graph = graph;
    }

    @Handle
    public void handle(
            @Required(name = "graphVertexId") String graphVertexId,
            @Optional(name = "width") Integer width,
            @ActiveWorkspaceId String workspaceId,
            User user,
            Authorizations authorizations,
            VisalloResponse response
    ) throws Exception {
        Vertex artifactVertex = graph.getVertex(graphVertexId, authorizations);
        if (artifactVertex == null) {
            throw new VisalloResourceNotFoundException("Could not find vertex with id: " + graphVertexId);
        }

        int[] boundaryDims = new int[]{200, 200};
        if (width != null) {
            boundaryDims[0] = boundaryDims[1] = width;
        }

        byte[] thumbnailData;
        Thumbnail thumbnail = thumbnailRepository.getThumbnail(
                artifactVertex.getId(),
                "raw",
                boundaryDims[0], boundaryDims[1],
                workspaceId,
                user);
        if (thumbnail != null) {
            String format = thumbnail.getFormat();
            response.setContentType("image/" + format);
            response.addHeader("Content-Disposition", "inline; filename=thumbnail" + boundaryDims[0] + "." + format);
            response.setMaxAge(VisalloResponse.EXPIRES_1_HOUR);

            thumbnailData = thumbnail.getData();
            if (thumbnailData != null) {
                LOGGER.debug("Cache hit for: %s (raw) %d x %d", artifactVertex.getId(), boundaryDims[0], boundaryDims[1]);
                try (OutputStream out = response.getOutputStream()) {
                    out.write(thumbnailData);
                }
                return;
            }
        }

        LOGGER.info("Cache miss for: %s (raw) %d x %d", artifactVertex.getId(), boundaryDims[0], boundaryDims[1]);
        Property rawProperty = VisalloProperties.RAW.getProperty(artifactVertex);
        StreamingPropertyValue rawPropertyValue = VisalloProperties.RAW.getPropertyValue(artifactVertex);
        if (rawPropertyValue == null) {
            throw new VisalloResourceNotFoundException("Could not find raw property on vertex: " + artifactVertex.getId());
        }

        try (InputStream in = rawPropertyValue.getInputStream()) {
            thumbnail = thumbnailRepository.createThumbnail(artifactVertex, rawProperty.getKey(), "raw", in, boundaryDims, user);

            String format = thumbnail.getFormat();
            response.setContentType("image/" + format);
            response.addHeader("Content-Disposition", "inline; filename=thumbnail" + boundaryDims[0] + "." + format);
            response.setMaxAge(VisalloResponse.EXPIRES_1_HOUR);

            thumbnailData = thumbnail.getData();
        }
        try (OutputStream out = response.getOutputStream()) {
            out.write(thumbnailData);
        }
    }
}
