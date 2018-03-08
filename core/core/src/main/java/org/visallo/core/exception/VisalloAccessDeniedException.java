package org.visallo.core.exception;

import org.visallo.core.bootstrap.InjectHelper;
import org.visallo.core.security.AuditService;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;

public class VisalloAccessDeniedException extends VisalloException {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(VisalloAccessDeniedException.class);
    private static final long serialVersionUID = -7805633940987966796L;
    private static AuditService auditService;
    private final User user;
    private final Object resourceId;

    public VisalloAccessDeniedException(String message, User user, Object resourceId) {
        super(message);
        this.user = user;
        this.resourceId = resourceId;
        try {
            AuditService auditService = getAuditService();
            auditService.auditAccessDenied(message, user, resourceId);
        } catch (Exception ex) {
            LOGGER.error(
                    "failed to audit access denied \"%s\" (userId: %s, resourceId: %s)",
                    message,
                    user == null ? "unknown" : user.getUserId(),
                    resourceId,
                    ex
            );
        }
    }

    private AuditService getAuditService() {
        if (auditService == null) {
            auditService = InjectHelper.getInstance(AuditService.class);
        }
        return auditService;
    }

    public User getUser() {
        return user;
    }

    public Object getResourceId() {
        return resourceId;
    }
}
