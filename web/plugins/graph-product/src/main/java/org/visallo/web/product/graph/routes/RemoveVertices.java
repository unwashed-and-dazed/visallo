package org.visallo.web.product.graph.routes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Optional;
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
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.web.VisalloResponse;
import org.visallo.web.clientapi.model.ClientApiSuccess;
import org.visallo.web.clientapi.model.ClientApiWorkspace;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;
import org.visallo.web.parameterProviders.SourceGuid;
import org.visallo.web.product.graph.GraphWorkProductService;
import org.visallo.web.product.graph.model.RemoveVerticesParams;

@Singleton
public class RemoveVertices implements ParameterizedHandler {
    private final Graph graph;
    private final WorkspaceRepository workspaceRepository;
    private final WorkQueueRepository workQueueRepository;
    private final AuthorizationRepository authorizationRepository;
    private final GraphRepository graphRepository;
    private final GraphWorkProductService graphWorkProductService;

    @Inject
    public RemoveVertices(
            Graph graph,
            WorkspaceRepository workspaceRepository,
            WorkQueueRepository workQueueRepository,
            AuthorizationRepository authorizationRepository,
            GraphRepository graphRepository,
            GraphWorkProductService graphWorkProductService
    ) {
        this.graph = graph;
        this.workspaceRepository = workspaceRepository;
        this.workQueueRepository = workQueueRepository;
        this.authorizationRepository = authorizationRepository;
        this.graphRepository = graphRepository;
        this.graphWorkProductService = graphWorkProductService;
    }

    @Handle
    public ClientApiSuccess handle(
            @Required(name = "vertexIds[]") String[] vertexIdsToRemove,
            @Required(name = "productId") String productId,
            @Optional(name = "params") String paramsStr,
            @ActiveWorkspaceId String workspaceId,
            @SourceGuid String sourceGuid,
            User user
    ) throws Exception {
        RemoveVerticesParams params = paramsStr == null
                ? new RemoveVerticesParams()
                : ClientApiConverter.toClientApi(paramsStr, RemoveVerticesParams.class);
        boolean removeChildren = params.isRemoveChildren();

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
        try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(Priority.HIGH, user, authorizations)) {
            Vertex productVertex = graph.getVertex(productId, authorizations);
            graphWorkProductService.removeVertices(
                    ctx,
                    productVertex,
                    vertexIdsToRemove,
                    removeChildren,
                    user,
                    WorkspaceRepository.VISIBILITY.getVisibility(),
                    authorizations
            );
        } catch (Exception e) {
            throw new VisalloException("Could not remove vertices from product: " + productId);
        }

        Workspace workspace = workspaceRepository.findById(workspaceId, user);
        ClientApiWorkspace clientApiWorkspace = workspaceRepository.toClientApi(workspace, user, authorizations);

        String skipSourceGuid = null;
        if (params.getBroadcastOptions() != null) {
            RemoveVerticesParams.BroadcastOptions broadcastOptions = params.getBroadcastOptions();
            if (broadcastOptions.isPreventBroadcastToSourceGuid()) {
                skipSourceGuid = sourceGuid;
            }
        }
        workQueueRepository.broadcastWorkProductChange(productId, clientApiWorkspace, user, skipSourceGuid);

        return VisalloResponse.SUCCESS;
    }
}
