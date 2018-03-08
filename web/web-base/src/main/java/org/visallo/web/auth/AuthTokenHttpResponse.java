package org.visallo.web.auth;

import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.CurrentUser;

import javax.crypto.SecretKey;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

public class AuthTokenHttpResponse extends HttpServletResponseWrapper {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(AuthTokenHttpResponse.class);
    private static final String EXPIRATION_HEADER_NAME = "Visallo-Auth-Token-Expiration";

    private final SecretKey macKey;
    private final HttpServletRequest request;
    private final long tokenValidityDurationInMinutes;
    private final AuthToken token;
    private boolean tokenCookieWritten = false;
    private boolean tokenHeaderWritten = false;

    public AuthTokenHttpResponse(AuthToken token, HttpServletRequest request, HttpServletResponse response, SecretKey macKey, long tokenValidityDurationInMinutes) {
        super(response);
        this.token = token;
        this.request = request;
        this.macKey = macKey;
        this.tokenValidityDurationInMinutes = tokenValidityDurationInMinutes;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        updateAuthToken();
        updateExpirationHeader();
        return super.getOutputStream();
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        updateAuthToken();
        updateExpirationHeader();
        return super.getWriter();
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        updateAuthToken();
        super.sendRedirect(location);
    }

    public void invalidateAuthentication() {
        if (isCommitted()) {
            throw new IllegalStateException("Unable to clear auth token. The response is already committed.");
        }
        writeAuthTokenCookie(null, 0);
    }

    private void updateExpirationHeader() {
        updateExpirationHeader(token);
    }

    private void updateExpirationHeader(AuthToken token) {
        if (!tokenHeaderWritten && token != null) {
            // Avoid client/server time differences by just sending seconds to expiration
            Long expiration = token.getExpiration().getTime() - System.currentTimeMillis();
            setHeader(EXPIRATION_HEADER_NAME, expiration.toString());
            tokenHeaderWritten = true;
        }
    }

    private void updateAuthToken() throws IOException {
        if (tokenCookieWritten ||
                (token != null && !isTokenNearingExpiration(token))) {
            return;
        }

        User currentUser = CurrentUser.get(request);

        if (currentUser != null) {
            Date tokenExpiration = calculateTokenExpiration();
            AuthToken token = new AuthToken(currentUser.getUserId(), macKey, tokenExpiration);

            try {
                writeAuthTokenCookie(token.serialize(), tokenValidityDurationInMinutes);
            } catch (AuthTokenException e) {
                LOGGER.error("Auth token serialization failed.", e);
                sendError(SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    private void writeAuthTokenCookie(String cookieValue, long cookieValidityInMinutes) {
        if (isCommitted()) {
            throw new IllegalStateException("Response committed before auth token cookie written.");
        }

        Cookie tokenCookie = new Cookie(AuthTokenFilter.TOKEN_COOKIE_NAME, cookieValue);
        tokenCookie.setMaxAge((int) cookieValidityInMinutes * 60);
        tokenCookie.setSecure(true);
        tokenCookie.setHttpOnly(true);
        tokenCookie.setPath("/");
        addCookie(tokenCookie);
        tokenCookieWritten = true;
    }

    private Date calculateTokenExpiration() {
        return new Date(System.currentTimeMillis() + (tokenValidityDurationInMinutes * 60 * 1000));
    }

    private boolean isTokenNearingExpiration(AuthToken token) {
        // nearing expiration if remaining time is less than half the token validity duration
        return (token.getExpiration().getTime() - System.currentTimeMillis()) < (tokenValidityDurationInMinutes * 60 * 1000 / 2);
    }
}
