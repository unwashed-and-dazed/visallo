package org.visallo.web.routes.workspace;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.vertexium.Authorizations;
import org.vertexium.SecurityVertexiumException;
import org.visallo.core.exception.VisalloAccessDeniedException;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.workspace.Workspace;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.clientapi.model.ClientApiWorkspace;

@Singleton
public class WorkspaceById implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(WorkspaceById.class);
    private final WorkspaceRepository workspaceRepository;

    @Inject
    public WorkspaceById(
            final WorkspaceRepository workspaceRepository
    ) {
        this.workspaceRepository = workspaceRepository;
    }

    @Handle
    public ClientApiWorkspace handle(
            @Required(name = "workspaceId") String workspaceId,
            User user,
            Authorizations authorizations
    ) throws Exception {
        LOGGER.info("Attempting to retrieve workspace: %s", workspaceId);
        try {
            final Workspace workspace = workspaceRepository.findById(workspaceId, user);
            if (workspace == null) {
                throw new VisalloResourceNotFoundException("Could not find workspace: " + workspaceId);
            } else {
                LOGGER.debug("Successfully found workspace");
                return workspaceRepository.toClientApi(workspace, user, authorizations);
            }
        } catch (SecurityVertexiumException ex) {
            throw new VisalloAccessDeniedException("Could not get workspace " + workspaceId, user, workspaceId);
        }
    }
}
