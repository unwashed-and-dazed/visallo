package org.visallo.web.product.map.routes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.vertexium.Vertex;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.graph.GraphRepository;
import org.visallo.core.model.graph.GraphUpdateContext;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.workQueue.Priority;
import org.visallo.core.model.workQueue.WorkQueueRepository;
import org.visallo.core.model.workspace.Workspace;
import org.visallo.core.model.workspace.WorkspaceHelper;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.model.workspace.product.UpdateProductEdgeOptions;
import org.visallo.core.security.VisibilityTranslator;
import org.visallo.core.user.User;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.web.VisalloResponse;
import org.visallo.web.clientapi.model.ClientApiSuccess;
import org.visallo.web.clientapi.model.ClientApiWorkspace;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;
import org.visallo.web.parameterProviders.SourceGuid;
import org.visallo.web.product.map.MapWorkProductService;

import java.util.Map;

@Singleton
public class UpdateVertices implements ParameterizedHandler {
    private final Graph graph;
    private final VisibilityTranslator visibilityTranslator;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceHelper workspaceHelper;
    private final WorkQueueRepository workQueueRepository;
    private final AuthorizationRepository authorizationRepository;
    private final GraphRepository graphRepository;
    private final MapWorkProductService mapWorkProductService;

    @Inject
    public UpdateVertices(
            Graph graph,
            VisibilityTranslator visibilityTranslator,
            WorkspaceRepository workspaceRepository,
            WorkspaceHelper workspaceHelper,
            WorkQueueRepository workQueueRepository,
            AuthorizationRepository authorizationRepository,
            GraphRepository graphRepository,
            MapWorkProductService mapWorkProductService
    ) {
        this.graph = graph;
        this.visibilityTranslator = visibilityTranslator;
        this.workspaceRepository = workspaceRepository;
        this.workspaceHelper = workspaceHelper;
        this.workQueueRepository = workQueueRepository;
        this.authorizationRepository = authorizationRepository;
        this.graphRepository = graphRepository;
        this.mapWorkProductService = mapWorkProductService;
    }

    @Handle
    public ClientApiSuccess handle(
            @Required(name = "updates") String updates,
            @Required(name = "productId") String productId,
            @ActiveWorkspaceId String workspaceId,
            @SourceGuid String sourceGuid,
            User user
    ) throws Exception {
        Map<String, UpdateProductEdgeOptions> updateVertices = ClientApiConverter.toClientApiMap(updates, UpdateProductEdgeOptions.class);
        Authorizations authorizations = authorizationRepository.getGraphAuthorizations(
                user,
                WorkspaceRepository.VISIBILITY_STRING,
                workspaceId
        );

        workspaceHelper.updateEntitiesOnWorkspace(
                workspaceId,
                updateVertices.keySet(),
                user
        );

        try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(Priority.HIGH, user, authorizations)) {
            Vertex productVertex = graph.getVertex(productId, authorizations);

            mapWorkProductService.updateVertices(ctx, productVertex, updateVertices, visibilityTranslator.getDefaultVisibility());
        } catch (Exception e) {
            throw new VisalloException("Could not update vertices in product: " + productId);
        }

        Workspace workspace = workspaceRepository.findById(workspaceId, user);
        ClientApiWorkspace clientApiWorkspace = workspaceRepository.toClientApi(workspace, user, authorizations);

        workQueueRepository.broadcastWorkProductChange(productId, clientApiWorkspace, user, sourceGuid);

        return VisalloResponse.SUCCESS;
    }
}
