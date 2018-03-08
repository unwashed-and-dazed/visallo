package org.visallo.core.security;

import com.google.inject.Singleton;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;

@Singleton
public class LoggingAuditService implements AuditService {
    private static final VisalloLogger AUDIT_LOGGER = VisalloLoggerFactory.getLogger("org.visallo.audit.AuditService");

    @Override
    public void auditLogin(User user) {
        if (AUDIT_LOGGER.isInfoEnabled()) {
            AUDIT_LOGGER.info("Login \"%s\" (username: %s)", user.getUserId(), user.getUsername());
        }
    }

    @Override
    public void auditLogout(String userId) {
        if (AUDIT_LOGGER.isInfoEnabled()) {
            AUDIT_LOGGER.info("Logout \"%s\"", userId);
        }
    }

    @Override
    public void auditAccessDenied(String message, User user, Object resourceId) {
        AUDIT_LOGGER.warn(
                "Access denied \"%s\" (userId: %s, resourceId: %s)",
                message,
                user == null ? "unknown" : user.getUserId(),
                resourceId
        );
    }
}
