package org.visallo.web.plugin.adminUserTools.userPropertyAuth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.json.JSONObject;
import org.visallo.core.exception.VisalloAccessDeniedException;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.user.UpdatableAuthorizationRepository;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.user.User;

@Singleton
public class UserAddAuthorization implements ParameterizedHandler {
    private final AuthorizationRepository authorizationRepository;
    private final UserRepository userRepository;
    private static final String SEPARATOR = ",";

    @Inject
    public UserAddAuthorization(
            UserRepository userRepository,
            AuthorizationRepository authorizationRepository
    ) {
        this.userRepository = userRepository;
        this.authorizationRepository = authorizationRepository;
    }

    @Handle
    public JSONObject handle(
            @Required(name = "user-name") String userName,
            @Required(name = "auth") String auth,
            User authUser
    ) throws Exception {
        User user = userRepository.findByUsername(userName);
        if (user == null) {
            throw new VisalloResourceNotFoundException("User " + userName + " not found");
        }

        if (!(authorizationRepository instanceof UpdatableAuthorizationRepository)) {
            throw new VisalloAccessDeniedException("Authorization repository does not support updating", authUser, userName);
        }

        for (String authStr : auth.split(SEPARATOR)) {
            ((UpdatableAuthorizationRepository) authorizationRepository).addAuthorization(user, authStr, authUser);
        }

        return userRepository.toJsonWithAuths(user);
    }
}
