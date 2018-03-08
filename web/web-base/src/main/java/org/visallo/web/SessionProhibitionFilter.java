package org.visallo.web;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;
import java.io.IOException;

public class SessionProhibitionFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // noop
    }

    @Override
    public void destroy() {
        // noop
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        chain.doFilter(new SessionProhibitionFilter.SessionProhibitionHttpRequestWrapper((HttpServletRequest)request), response);
    }

    private class SessionProhibitionHttpRequestWrapper extends HttpServletRequestWrapper {
        public static final String ERROR_MSG = "javax.servlet.http.HttpSession use is prohibited";

        public SessionProhibitionHttpRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public HttpSession getSession(boolean create) {
            if (create) {
                throw new UnsupportedOperationException(ERROR_MSG);
            }
            // Atmosphere calls get session(false), otherwise websockets won't work
            return null;
        }

        @Override
        public HttpSession getSession() {
            throw new UnsupportedOperationException(ERROR_MSG);
        }

        @Override
        public String getRequestedSessionId() {
            return null;
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromURL() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromUrl() {
            return false;
        }

        @Override
        public String changeSessionId() {
            throw new IllegalStateException(ERROR_MSG);
        }
    }
}
