package org.visallo.web.webEventListeners;

import org.visallo.web.CurrentUser;
import org.visallo.web.WebApp;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CurrentUserWebEventListener extends DefaultWebEventListener {
    public static final int PRIORITY = -1000;

    @Override
    public void before(WebApp app, HttpServletRequest request, HttpServletResponse response) {
        CurrentUser.setUserInLogMappedDiagnosticContexts(request);
    }

    @Override
    public void always(WebApp app, HttpServletRequest request, HttpServletResponse response) {
        CurrentUser.clearUserFromLogMappedDiagnosticContexts();
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
