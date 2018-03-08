package org.visallo.web.plugin.adminUserTools;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.vertexium.Graph;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.VisalloResponse;
import org.visallo.web.clientapi.model.ClientApiSuccess;

@Singleton
public class UserDelete implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(UserDelete.class);
    private final Graph graph;
    private final UserRepository userRepository;

    @Inject
    public UserDelete(final Graph graph, final UserRepository userRepository) {
        this.graph = graph;
        this.userRepository = userRepository;
    }

    @Handle
    public ClientApiSuccess handle(
            @Required(name = "user-name") String userName
    ) throws Exception {
        User user = userRepository.findByUsername(userName);
        if (user == null) {
            throw new VisalloResourceNotFoundException("Could find user: " + userName);
        }

        LOGGER.info("deleting user %s", user.getUserId());
        userRepository.delete(user);
        this.graph.flush();

        return VisalloResponse.SUCCESS;
    }
}
