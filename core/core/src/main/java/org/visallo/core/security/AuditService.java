package org.visallo.core.security;

import org.visallo.core.user.User;

public interface AuditService {
    void auditLogin(User user);

    void auditLogout(String userId);

    void auditAccessDenied(String message, User user, Object resourceId);
}
