package org.visallo.web.routes.workspace;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.vertexium.Authorizations;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.workQueue.WorkQueueRepository;
import org.visallo.core.model.workspace.Workspace;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.VisalloResponse;
import org.visallo.web.clientapi.model.ClientApiSuccess;
import org.visallo.web.clientapi.model.ClientApiWorkspace;

@Singleton
public class WorkspaceDelete implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(WorkspaceDelete.class);
    private final WorkspaceRepository workspaceRepository;
    private final WorkQueueRepository workQueueRepository;

    @Inject
    public WorkspaceDelete(
            final WorkspaceRepository workspaceRepository,
            final WorkQueueRepository workQueueRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workQueueRepository = workQueueRepository;
    }

    @Handle
    public ClientApiSuccess handle(
            @Required(name = "workspaceId") String workspaceId,
            User user,
            Authorizations authorizations
    ) throws Exception {
        LOGGER.info("Deleting workspace with id: %s", workspaceId);
        Workspace workspace = workspaceRepository.findById(workspaceId, user);
        if (workspace == null) {
            throw new VisalloResourceNotFoundException("Could not find workspace: " + workspaceId);
        }
        ClientApiWorkspace clientApiWorkspaceBeforeDeletion = workspaceRepository.toClientApi(workspace, user, authorizations);
        workspaceRepository.delete(workspace, user);
        workQueueRepository.pushWorkspaceDelete(clientApiWorkspaceBeforeDeletion);

        return VisalloResponse.SUCCESS;
    }
}
