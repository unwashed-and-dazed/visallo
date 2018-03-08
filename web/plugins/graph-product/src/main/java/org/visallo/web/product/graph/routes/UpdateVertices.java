package org.visallo.web.product.graph.routes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.vertexium.Vertex;
import org.visallo.core.exception.VisalloAccessDeniedException;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.graph.GraphRepository;
import org.visallo.core.model.graph.GraphUpdateContext;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.workQueue.Priority;
import org.visallo.core.model.workQueue.WorkQueueRepository;
import org.visallo.core.model.workspace.Workspace;
import org.visallo.core.model.workspace.WorkspaceHelper;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.web.VisalloResponse;
import org.visallo.web.clientapi.model.ClientApiSuccess;
import org.visallo.web.clientapi.model.ClientApiWorkspace;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;
import org.visallo.web.parameterProviders.SourceGuid;
import org.visallo.web.product.graph.GraphWorkProductService;
import org.visallo.web.product.graph.model.GraphUpdateProductEdgeOptions;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class UpdateVertices implements ParameterizedHandler {
    private final Graph graph;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceHelper workspaceHelper;
    private final WorkQueueRepository workQueueRepository;
    private final AuthorizationRepository authorizationRepository;
    private final GraphRepository graphRepository;
    private final GraphWorkProductService graphWorkProductService;

    @Inject
    public UpdateVertices(
            Graph graph,
            WorkspaceRepository workspaceRepository,
            WorkspaceHelper workspaceHelper,
            WorkQueueRepository workQueueRepository,
            AuthorizationRepository authorizationRepository,
            GraphRepository graphRepository,
            GraphWorkProductService graphWorkProductService
    ) {
        this.graph = graph;
        this.workspaceRepository = workspaceRepository;
        this.workspaceHelper = workspaceHelper;
        this.workQueueRepository = workQueueRepository;
        this.authorizationRepository = authorizationRepository;
        this.graphRepository = graphRepository;
        this.graphWorkProductService = graphWorkProductService;
    }

    @Handle
    public ClientApiSuccess handle(
            @Required(name = "updates") String updates,
            @Required(name = "productId") String productId,
            @ActiveWorkspaceId String workspaceId,
            @SourceGuid String sourceGuid,
            User user
    ) throws Exception {
        Map<String, GraphUpdateProductEdgeOptions> updateVertices = ClientApiConverter.toClientApiMap(updates, GraphUpdateProductEdgeOptions.class);

        if (!workspaceRepository.hasWritePermissions(workspaceId, user)) {
            throw new VisalloAccessDeniedException(
                    "user " + user.getUserId() + " does not have write access to workspace " + workspaceId,
                    user,
                    workspaceId
            );
        }

        Authorizations authorizations = authorizationRepository.getGraphAuthorizations(
                user,
                WorkspaceRepository.VISIBILITY_STRING,
                workspaceId
        );

        Set<String> vertices = updateVertices.keySet();
        vertices = vertices.stream()
                .filter(id -> !updateVertices.get(id).hasChildren())
                .collect(Collectors.toSet());
        workspaceHelper.updateEntitiesOnWorkspace(
                workspaceId,
                vertices,
                user
        );

        try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(Priority.HIGH, user, authorizations)) {
            Vertex productVertex = workspaceRepository.getProductVertex(workspaceId, productId, user);
            graphWorkProductService.updateVertices(ctx, productVertex, updateVertices, user, WorkspaceRepository.VISIBILITY.getVisibility(), authorizations);
        } catch (Exception e) {
            throw new VisalloException("Could not update vertices in product: " + productId);
        }

        Workspace workspace = workspaceRepository.findById(workspaceId, user);
        ClientApiWorkspace clientApiWorkspace = workspaceRepository.toClientApi(workspace, user, authorizations);

        workQueueRepository.broadcastWorkProductChange(productId, clientApiWorkspace, user, sourceGuid);

        return VisalloResponse.SUCCESS;
    }
}
