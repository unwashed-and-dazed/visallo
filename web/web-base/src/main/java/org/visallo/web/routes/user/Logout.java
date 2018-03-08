package org.visallo.web.routes.user;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.core.security.AuditService;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.CurrentUser;
import org.visallo.web.VisalloResponse;
import org.visallo.web.auth.AuthTokenHttpResponse;
import org.visallo.web.clientapi.model.ClientApiSuccess;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class Logout implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(Logout.class);

    private final AuditService auditService;

    @Inject
    public Logout(
            AuditService auditService
    ) {
        this.auditService = auditService;
    }

    @Handle
    public ClientApiSuccess handle(HttpServletRequest request, HttpServletResponse response) throws Exception {
        User user = CurrentUser.get(request);
        if (user != null) {
            auditService.auditLogout(user.getUserId());
            CurrentUser.set(request, null);

            if (response instanceof AuthTokenHttpResponse) {
                AuthTokenHttpResponse authResponse = (AuthTokenHttpResponse) response;
                authResponse.invalidateAuthentication();
            } else {
                LOGGER.error("Logout called but response is not an instance of %s. User may not actually be logged out.", AuthTokenHttpResponse.class.getName());
            }
        }

        return VisalloResponse.SUCCESS;
    }
}
