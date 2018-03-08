package org.visallo.web.routes.vertex;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.vertexium.Visibility;
import org.visallo.core.model.termMention.TermMentionRepository;
import org.visallo.web.clientapi.model.ClientApiSourceInfo;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.vertexium.Vertex;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.graph.GraphRepository;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.user.PrivilegeRepository;
import org.visallo.core.model.workQueue.Priority;
import org.visallo.core.model.workQueue.WorkQueueRepository;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.security.VisibilityTranslator;
import org.visallo.core.user.User;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.core.util.SandboxStatusUtil;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.clientapi.model.ClientApiVertex;
import org.visallo.web.clientapi.model.Privilege;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;
import org.visallo.web.parameterProviders.SourceGuid;
import org.visallo.web.util.VisibilityValidator;

import java.util.ResourceBundle;
import java.util.Set;

@Singleton
public class VertexSetVisibility implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(VertexSetVisibility.class);
    private final Graph graph;
    private final WorkspaceRepository workspaceRepository;
    private final WorkQueueRepository workQueueRepository;
    private final GraphRepository graphRepository;
    private final VisibilityTranslator visibilityTranslator;
    private final PrivilegeRepository privilegeRepository;
    private final TermMentionRepository termMentionRepository;

    @Inject
    public VertexSetVisibility(
            Graph graph,
            WorkspaceRepository workspaceRepository,
            WorkQueueRepository workQueueRepository,
            GraphRepository graphRepository,
            PrivilegeRepository privilegeRepository,
            VisibilityTranslator visibilityTranslator,
            TermMentionRepository termMentionRepository
    ) {
        this.graph = graph;
        this.workspaceRepository = workspaceRepository;
        this.workQueueRepository = workQueueRepository;
        this.graphRepository = graphRepository;
        this.visibilityTranslator = visibilityTranslator;
        this.privilegeRepository = privilegeRepository;
        this.termMentionRepository = termMentionRepository;
    }

    @Handle
    public ClientApiVertex handle(
            @Required(name = "graphVertexId") String graphVertexId,
            @Required(name = "visibilitySource") String visibilitySource,
            @ActiveWorkspaceId String workspaceId,
            @SourceGuid String sourceGuid,
            ResourceBundle resourceBundle,
            User user,
            Authorizations authorizations
    ) throws Exception {
        VisibilityValidator.validate(graph, visibilityTranslator, resourceBundle, visibilitySource, user, authorizations);

        Vertex graphVertex = graph.getVertex(graphVertexId, authorizations);
        if (graphVertex == null) {
            throw new VisalloResourceNotFoundException("Could not find vertex: " + graphVertexId);
        }

        // add the vertex to the workspace so that the changes show up in the diff panel
        workspaceRepository.updateEntityOnWorkspace(workspaceId, graphVertexId, user);

        LOGGER.info("changing vertex (%s) visibility source to %s", graphVertex.getId(), visibilitySource);

        Set<String> privileges = privilegeRepository.getPrivileges(user);
        if (!privileges.contains(Privilege.PUBLISH)) {
            throw new VisalloException("User does not have access to modify the visibility");
        }

        graphRepository.updateElementVisibilitySource(
                graphVertex,
                SandboxStatusUtil.getSandboxStatus(graphVertex, workspaceId),
                visibilitySource,
                workspaceId,
                authorizations
        );

        this.graph.flush();

        this.workQueueRepository.pushGraphPropertyQueue(
                graphVertex,
                null,
                VisalloProperties.VISIBILITY_JSON.getPropertyName(),
                workspaceId,
                visibilitySource,
                Priority.HIGH
        );

        ClientApiSourceInfo sourceInfo = termMentionRepository.getSourceInfoForVertex(graphVertex, authorizations);
        if (sourceInfo != null) {
            termMentionRepository.updateEdgeVisibility(graphVertexId, visibilitySource, workspaceId, authorizations);
            workQueueRepository.pushTextUpdated(sourceInfo.vertexId);
        }

        return (ClientApiVertex) ClientApiConverter.toClientApi(graphVertex, workspaceId, authorizations);
    }
}
