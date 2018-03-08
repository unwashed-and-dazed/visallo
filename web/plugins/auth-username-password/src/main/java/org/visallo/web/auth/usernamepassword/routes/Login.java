package org.visallo.web.auth.usernamepassword.routes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.json.JSONObject;
import org.visallo.core.exception.VisalloAccessDeniedException;
import org.visallo.core.model.user.UserNameAuthorizationContext;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.security.AuditService;
import org.visallo.core.user.User;
import org.visallo.web.CurrentUser;
import org.visallo.web.util.RemoteAddressUtil;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;

import javax.servlet.http.HttpServletRequest;

@Singleton
public class Login implements ParameterizedHandler {
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Inject
    public Login(
            UserRepository userRepository,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Handle
    public JSONObject handle(
            HttpServletRequest request,
            @Required(name = "username") String username,
            @Required(name = "password") String password
    ) {
        username = username.trim();
        password = password.trim();

        User user = userRepository.findByUsername(username);
        if (user != null && userRepository.isPasswordValid(user, password)) {
            UserNameAuthorizationContext authorizationContext = new UserNameAuthorizationContext(
                    username,
                    RemoteAddressUtil.getClientIpAddr(request)
            );
            userRepository.updateUser(user, authorizationContext);
            CurrentUser.set(request, user);
            auditService.auditLogin(user);
            JSONObject json = new JSONObject();
            json.put("status", "OK");
            return json;
        } else {
            throw new VisalloAccessDeniedException("", user, null);
        }
    }
}
