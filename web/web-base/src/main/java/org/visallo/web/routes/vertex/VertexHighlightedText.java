package org.visallo.web.routes.vertex;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.vertexium.*;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Optional;
import org.visallo.webster.annotations.Required;
import org.vertexium.property.StreamingPropertyValue;
import org.visallo.core.EntityHighlighter;
import org.visallo.core.config.Configuration;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.ingest.video.VideoTranscript;
import org.visallo.core.model.properties.MediaVisalloProperties;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.termMention.TermMentionRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.JsonSerializer;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.VisalloResponse;
import org.visallo.web.WebConfiguration;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.visallo.core.util.StreamUtil.stream;

@Singleton
public class VertexHighlightedText implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(VertexHighlightedText.class);
    private final Graph graph;
    private final EntityHighlighter entityHighlighter;
    private final TermMentionRepository termMentionRepository;
    private final Configuration configuration;

    @Inject
    public VertexHighlightedText(
            final Graph graph,
            final EntityHighlighter entityHighlighter,
            final TermMentionRepository termMentionRepository,
            final Configuration configuration
            ) {
        this.graph = graph;
        this.entityHighlighter = entityHighlighter;
        this.termMentionRepository = termMentionRepository;
        this.configuration = configuration;
    }

    @Handle
    public void handle(
            @Required(name = "graphVertexId") String graphVertexId,
            @Required(name = "propertyKey") String propertyKey,
            @Optional(name = "propertyName") String propertyName,
            @ActiveWorkspaceId String workspaceId,
            User user,
            Authorizations authorizations,
            VisalloResponse response
    ) throws Exception {
        Authorizations authorizationsWithTermMention = termMentionRepository.getAuthorizations(authorizations);

        Vertex artifactVertex = graph.getVertex(graphVertexId, authorizations);
        if (artifactVertex == null) {
            throw new VisalloResourceNotFoundException("Could not find vertex with id: " + graphVertexId);
        }

        if (Strings.isNullOrEmpty(propertyName)) {
            propertyName = VisalloProperties.TEXT.getPropertyName();
        }

        Long maxTextLength = configuration.getLong(WebConfiguration.MAX_TEXT_LENGTH, -1L);

        StreamingPropertyValue textPropertyValue = (StreamingPropertyValue) artifactVertex.getPropertyValue(propertyKey, propertyName);
        if (textPropertyValue != null) {
            response.setContentType("text/html");

            LOGGER.debug("returning text for vertexId:%s property:%s", artifactVertex.getId(), propertyKey);
            InputStream inputStream = textPropertyValue.getInputStream();

            if (inputStream == null) {
                response.respondWithHtml("");
            } else {
                Iterable<Vertex> termMentions = termMentionRepository.findByOutVertexAndProperty(artifactVertex.getId(), propertyKey, propertyName, authorizationsWithTermMention);
                List<String> resolvedToVertexIds = stream(termMentions)
                        .map(VisalloProperties.TERM_MENTION_FOR_ELEMENT_ID::getPropertyValue)
                        .filter(id -> id != null)
                        .collect(Collectors.toList());
                Map<String, Boolean> resolvedVerticesExist = graph.doVerticesExist(resolvedToVertexIds, authorizations);

                termMentions = stream(termMentions)
                        .filter(termMention -> {
                            String resolvedToVertexId = VisalloProperties.TERM_MENTION_FOR_ELEMENT_ID.getPropertyValue(termMention);
                            return resolvedToVertexId == null || resolvedVerticesExist.getOrDefault(resolvedToVertexId, false);
                        }).collect(Collectors.toList());

                entityHighlighter.transformHighlightedText(inputStream, response.getOutputStream(), termMentions, maxTextLength, workspaceId, authorizationsWithTermMention);
            }
        }

        VideoTranscript videoTranscript = MediaVisalloProperties.VIDEO_TRANSCRIPT.getPropertyValue(artifactVertex, propertyKey);
        if (videoTranscript != null) {
            LOGGER.debug("returning video transcript for vertexId:%s property:%s", artifactVertex.getId(), propertyKey);
            Iterable<Vertex> termMentions = termMentionRepository.findByOutVertexAndProperty(artifactVertex.getId(), propertyKey, propertyName, authorizations);
            VideoTranscript highlightedVideoTranscript = entityHighlighter.getHighlightedVideoTranscript(videoTranscript, termMentions, workspaceId, authorizations);
            response.setContentType("application/json");
            response.respondWithJson(highlightedVideoTranscript.toJson());
        }

        videoTranscript = JsonSerializer.getSynthesisedVideoTranscription(artifactVertex, propertyKey);
        if (videoTranscript != null) {
            LOGGER.debug("returning synthesised video transcript for vertexId:%s property:%s", artifactVertex.getId(), propertyKey);
            Iterable<Vertex> termMentions = termMentionRepository.findByOutVertexAndProperty(artifactVertex.getId(), propertyKey, propertyName, authorizations);
            VideoTranscript highlightedVideoTranscript = entityHighlighter.getHighlightedVideoTranscript(videoTranscript, termMentions, workspaceId, authorizationsWithTermMention);
            response.setContentType("application/json");
            response.respondWithJson(highlightedVideoTranscript.toJson());
        }
    }
}
