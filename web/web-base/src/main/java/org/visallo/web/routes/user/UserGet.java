package org.visallo.web.routes.user;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.vertexium.Authorizations;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.model.user.UserSessionCounterRepository;
import org.visallo.core.model.workspace.Workspace;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.clientapi.model.ClientApiUser;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;

@Singleton
public class UserGet implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(UserGet.class);

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AuthorizationRepository authorizationRepository;
    private final UserSessionCounterRepository userSessionCounterRepository;

    @Inject
    public UserGet(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            AuthorizationRepository authorizationRepository,
            UserSessionCounterRepository userSessionCounterRepository
    ) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.authorizationRepository = authorizationRepository;
        this.userSessionCounterRepository = userSessionCounterRepository;
    }

    @Handle
    public ClientApiUser handle(
            @Required(name = "user-name") String userName
    ) throws Exception {
        User user = userRepository.findByUsername(userName);
        if (user == null) {
            throw new VisalloResourceNotFoundException("user not found");
        }

        Authorizations authorizations = authorizationRepository.getGraphAuthorizations(user);

        ClientApiUser clientApiUser = userRepository.toClientApiPrivate(user);

        addSessionCount(clientApiUser);

        Iterable<Workspace> workspaces = workspaceRepository.findAllForUser(user);
        for (Workspace workspace : workspaces) {
            clientApiUser.getWorkspaces().add(workspaceRepository.toClientApi(workspace, user, authorizations));
        }

        return clientApiUser;
    }

    private void addSessionCount(ClientApiUser user) {
        String id = user.getId();
        try {
            int count = userSessionCounterRepository.getSessionCount(id);
            user.setSessionCount(count);
        } catch (VisalloException e) {
            LOGGER.error("Error getting session count for userId: %s", id, e);
        }
    }
}
