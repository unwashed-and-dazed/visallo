package org.visallo.web.routes.workspace;

import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.vertexium.Authorizations;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.clientapi.model.ClientApiPublishItem;
import org.visallo.web.clientapi.model.ClientApiWorkspacePublishResponse;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

@Singleton
public class WorkspacePublish implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(WorkspacePublish.class);
    private final WorkspaceRepository workspaceRepository;

    @Inject
    public WorkspacePublish(final WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Handle
    public ClientApiWorkspacePublishResponse handle(
            @Required(name = "publishData") ClientApiPublishItem[] publishData,
            @ActiveWorkspaceId String workspaceId,
            User user,
            Authorizations authorizations
    ) throws Exception {
        LOGGER.debug("publishing:\n%s", Joiner.on("\n").join(publishData));
        ClientApiWorkspacePublishResponse workspacePublishResponse = workspaceRepository.publish(publishData, user, workspaceId, authorizations);

        LOGGER.debug("publishing results: %s", workspacePublishResponse);
        return workspacePublishResponse;
    }
}
