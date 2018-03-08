package org.visallo.web.routes.vertex;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Optional;
import org.visallo.webster.annotations.Required;
import org.apache.commons.io.IOUtils;
import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.vertexium.Property;
import org.vertexium.Vertex;
import org.vertexium.property.StreamingPropertyValue;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.properties.MediaVisalloProperties;
import org.visallo.core.model.thumbnails.ThumbnailRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.VisalloResponse;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

import java.io.InputStream;
import java.io.OutputStream;

@Singleton
public class VertexPosterFrame implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(VertexPosterFrame.class);
    private final Graph graph;
    private final ThumbnailRepository thumbnailRepository;

    @Inject
    public VertexPosterFrame(
            final Graph graph,
            final ThumbnailRepository thumbnailRepository
    ) {
        this.graph = graph;
        this.thumbnailRepository = thumbnailRepository;
    }

    @Handle
    public void handle(
            @Required(name = "graphVertexId") String graphVertexId,
            @Optional(name = "width") Integer width,
            @ActiveWorkspaceId String workspaceId,
            Authorizations authorizations,
            User user,
            VisalloResponse response
    ) throws Exception {
        int[] boundaryDims = new int[]{200, 200};

        Vertex artifactVertex = graph.getVertex(graphVertexId, authorizations);
        if (artifactVertex == null) {
            throw new VisalloResourceNotFoundException("Could not find vertex with id: " + graphVertexId);
        }

        if (width != null) {
            boundaryDims[0] = boundaryDims[1] = width;

            response.setContentType("image/jpeg");
            response.addHeader("Content-Disposition", "inline; filename=thumbnail" + boundaryDims[0] + ".jpg");
            response.setMaxAge(VisalloResponse.EXPIRES_1_HOUR);

            byte[] thumbnailData = thumbnailRepository.getThumbnailData(
                    artifactVertex.getId(),
                    "poster-frame",
                    boundaryDims[0], boundaryDims[1],
                    workspaceId,
                    user);
            if (thumbnailData != null) {
                LOGGER.debug("Cache hit for: %s (poster-frame) %d x %d", graphVertexId, boundaryDims[0], boundaryDims[1]);
                try (OutputStream out = response.getOutputStream()) {
                    out.write(thumbnailData);
                }
                return;
            }
        }

        Property rawPosterFrame = MediaVisalloProperties.RAW_POSTER_FRAME.getOnlyProperty(artifactVertex);
        StreamingPropertyValue rawPosterFrameValue = MediaVisalloProperties.RAW_POSTER_FRAME.getPropertyValue(rawPosterFrame);
        if (rawPosterFrameValue == null) {
            throw new VisalloResourceNotFoundException("Could not find raw poster from for artifact: " + artifactVertex.getId());
        }

        try (InputStream in = rawPosterFrameValue.getInputStream()) {
            if (width != null) {
                LOGGER.info("Cache miss for: %s (poster-frame) %d x %d", graphVertexId, boundaryDims[0], boundaryDims[1]);

                response.setContentType("image/jpeg");
                response.addHeader("Content-Disposition", "inline; filename=thumbnail" + boundaryDims[0] + ".jpg");
                response.setMaxAge(VisalloResponse.EXPIRES_1_HOUR);

                byte[] thumbnailData = thumbnailRepository.createThumbnail(artifactVertex, rawPosterFrame.getKey(), "poster-frame", in, boundaryDims, user).getData();
                try (OutputStream out = response.getOutputStream()) {
                    out.write(thumbnailData);
                }
            } else {
                response.setContentType("image/png");
                try (OutputStream out = response.getOutputStream()) {
                    IOUtils.copy(in, out);
                }
            }
        }
    }
}
