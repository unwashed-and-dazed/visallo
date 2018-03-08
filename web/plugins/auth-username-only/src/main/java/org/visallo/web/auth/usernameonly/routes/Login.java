package org.visallo.web.auth.usernameonly.routes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.json.JSONObject;
import org.visallo.core.model.user.AuthorizationContext;
import org.visallo.core.model.user.UserNameAuthorizationContext;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.security.AuditService;
import org.visallo.core.user.User;
import org.visallo.web.CurrentUser;
import org.visallo.web.util.RemoteAddressUtil;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.utils.UrlUtils;

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
    public JSONObject handle(HttpServletRequest request) {
        final String username = UrlUtils.urlDecode(request.getParameter("username")).trim().toLowerCase();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            // For form based authentication, username and displayName will be the same
            String randomPassword = UserRepository.createRandomPassword();
            user = userRepository.findOrAddUser(
                    username,
                    username,
                    null,
                    randomPassword
            );
        }

        AuthorizationContext authorizationContext = new UserNameAuthorizationContext(
                username,
                RemoteAddressUtil.getClientIpAddr(request)
        );
        userRepository.updateUser(user, authorizationContext);

        CurrentUser.set(request, user);
        auditService.auditLogin(user);
        JSONObject json = new JSONObject();
        json.put("status", "OK");
        return json;
    }
}
