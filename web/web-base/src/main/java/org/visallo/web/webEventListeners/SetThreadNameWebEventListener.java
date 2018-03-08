package org.visallo.web.webEventListeners;

import org.visallo.core.util.VisalloPlugin;
import org.visallo.web.WebApp;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@VisalloPlugin(disabledByDefault = true)
public class SetThreadNameWebEventListener extends DefaultWebEventListener {
    public static final int PRIORITY = -1000;
    private static final String THREAD_NAME_PREFIX = "http-";

    @Override
    public void before(WebApp app, HttpServletRequest request, HttpServletResponse response) {
        Thread.currentThread().setName(getNewThreadName(request));
    }

    private String getNewThreadName(HttpServletRequest request) {
        return THREAD_NAME_PREFIX + request.getRequestURI();
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
