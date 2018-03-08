package org.visallo.web.webEventListeners;

import org.visallo.web.WebApp;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public abstract class DefaultWebEventListener implements WebEventListener {
    @Override
    public void before(WebApp app, HttpServletRequest request, HttpServletResponse response) {

    }

    @Override
    public void after(WebApp app, HttpServletRequest request, HttpServletResponse response) {

    }

    @Override
    public void error(WebApp app, HttpServletRequest request, HttpServletResponse response, Throwable error) throws ServletException, IOException {

    }

    @Override
    public void always(WebApp app, HttpServletRequest request, HttpServletResponse response) {

    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }
}
