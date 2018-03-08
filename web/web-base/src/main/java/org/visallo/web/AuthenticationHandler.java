package org.visallo.web;

import org.visallo.core.user.User;
import org.visallo.web.util.RemoteAddressUtil;
import org.visallo.webster.HandlerChain;
import org.visallo.webster.RequestResponseHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AuthenticationHandler implements RequestResponseHandler {
    public static final String LOGIN_PATH = "/login";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        User currentUser = CurrentUser.get(request);
        if (currentUser != null) {
            chain.next(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    /**
     * @Deprecated
     *
     * Use RemoteAddressUtil.getClientIpAddr for future calls to get client IP addresses.
     */
    @Deprecated
    public static String getRemoteAddr(HttpServletRequest request) {
        return RemoteAddressUtil.getClientIpAddr(request);
    }
}
