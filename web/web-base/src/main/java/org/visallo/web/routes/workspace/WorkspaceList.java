package org.visallo.web.routes.workspace;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.vertexium.Authorizations;
import org.vertexium.SecurityVertexiumException;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.workspace.Workspace;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.web.clientapi.model.ClientApiProduct;
import org.visallo.web.clientapi.model.ClientApiWorkspace;
import org.visallo.web.clientapi.model.ClientApiWorkspaces;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Optional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.vertexium.util.IterableUtils.toList;

@Singleton
public class WorkspaceList implements ParameterizedHandler {
    private final WorkspaceRepository workspaceRepository;
    private final AuthorizationRepository authorizationRepository;

    @Inject
    public WorkspaceList(
            WorkspaceRepository workspaceRepository,
            AuthorizationRepository authorizationRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.authorizationRepository = authorizationRepository;
    }

    @Handle
    public ClientApiWorkspaces handle(
            @ActiveWorkspaceId(required = false) String activeWorkspaceId,
            @Optional(name = "includeProducts", defaultValue = "false") boolean includeProducts,
            User user
    ) throws Exception {
        Authorizations authorizations;

        if (hasAccess(activeWorkspaceId, user)) {
            authorizations = authorizationRepository.getGraphAuthorizations(user, activeWorkspaceId);
        } else {
            authorizations = authorizationRepository.getGraphAuthorizations(user);
        }

        List<Workspace> workspaces = toList(workspaceRepository.findAllForUser(user));

        Map<String, String> lastActiveProductIdsByWorkspaceId = null;
        if (includeProducts) {
            lastActiveProductIdsByWorkspaceId = workspaceRepository.getLastActiveProductIdsByWorkspaceId(
                    workspaces.stream().map(Workspace::getWorkspaceId).collect(Collectors.toList()),
                    user
            );
        }

        ClientApiWorkspaces results = new ClientApiWorkspaces();
        for (Workspace workspace : workspaces) {
            ClientApiWorkspace workspaceClientApi = workspaceRepository.toClientApi(
                    workspace,
                    user,
                    authorizations
            );
            if (workspaceClientApi != null) {
                workspaceClientApi.setActive(workspace.getWorkspaceId().equals(user.getCurrentWorkspaceId()));
                results.addWorkspace(workspaceClientApi);
                if (includeProducts) {
                    String lastActiveProductId = lastActiveProductIdsByWorkspaceId.get(workspace.getWorkspaceId());
                    Collection<ClientApiProduct> products = workspaceRepository.findAllProductsForWorkspace(workspace.getWorkspaceId(), user).stream()
                            .map(ClientApiConverter::toClientApiProduct)
                            .peek(product -> product.active = product.id.equals(lastActiveProductId))
                            .collect(Collectors.toList());
                    workspaceClientApi.setProducts(products);
                }
            }
        }
        return results;
    }

    private boolean hasAccess(String workspaceId, User user) {
        try {
            return workspaceId != null && workspaceRepository.hasReadPermissions(workspaceId, user);
        } catch (SecurityVertexiumException e) {
            return false;
        }
    }
}
