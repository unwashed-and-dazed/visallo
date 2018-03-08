package org.visallo.web.auth;

import org.apache.commons.lang.StringUtils;
import org.visallo.core.bootstrap.InjectHelper;
import org.visallo.core.config.Configuration;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.CurrentUser;

import javax.crypto.SecretKey;
import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.visallo.core.config.Configuration.*;

public class AuthTokenFilter implements Filter {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(AuthTokenFilter.class);
    private static final int MIN_AUTH_TOKEN_EXPIRATION_MINS = 1;
    public static final String TOKEN_COOKIE_NAME = "JWT";

    private SecretKey tokenSigningKey;
    private long tokenValidityDurationInMinutes;
    private int tokenExpirationToleranceInSeconds;
    private UserRepository userRepository;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        tokenValidityDurationInMinutes = Long.parseLong(
                getRequiredInitParameter(filterConfig, AUTH_TOKEN_EXPIRATION_IN_MINS)
        );
        if (tokenValidityDurationInMinutes < MIN_AUTH_TOKEN_EXPIRATION_MINS) {
            throw new VisalloException("Configuration: " +
                "'" +  AUTH_TOKEN_EXPIRATION_IN_MINS + "' " +
                "must be at least " + MIN_AUTH_TOKEN_EXPIRATION_MINS + " minute(s)"
            );
        }

        tokenExpirationToleranceInSeconds = Integer.parseInt(
                getRequiredInitParameter(filterConfig, Configuration.AUTH_TOKEN_EXPIRATION_TOLERANCE_IN_SECS)
        );

        String keyPassword = getRequiredInitParameter(filterConfig, AUTH_TOKEN_PASSWORD);
        String keySalt = getRequiredInitParameter(filterConfig, AUTH_TOKEN_SALT);
        userRepository = InjectHelper.getInstance(UserRepository.class);

        try {
            tokenSigningKey = AuthToken.generateKey(keyPassword, keySalt);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException {
        doFilter((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse, filterChain);
    }

    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException {
        try {
            AuthToken token = getAuthToken(request);
            AuthTokenHttpResponse authTokenResponse = new AuthTokenHttpResponse(token, request, response, tokenSigningKey, tokenValidityDurationInMinutes);

            if (token != null) {
                if (token.isExpired(tokenExpirationToleranceInSeconds)) {
                    authTokenResponse.invalidateAuthentication();
                } else {
                    User user = userRepository.findById(token.getUserId());
                    if (user != null) {
                        CurrentUser.set(request, user);
                    } else {
                        authTokenResponse.invalidateAuthentication();
                    }
                }
            }

            chain.doFilter(request, authTokenResponse);
        } catch (Exception ex) {
            LOGGER.warn("Auth token signature verification failed", ex);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    @Override
    public void destroy() {

    }

    private AuthToken getAuthToken(HttpServletRequest request) throws AuthTokenException {
        Cookie tokenCookie = getTokenCookie(request);
        return tokenCookie != null ? AuthToken.parse(tokenCookie.getValue(), tokenSigningKey) : null;
    }

    private Cookie getTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        Cookie found = null;

        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(AuthTokenFilter.TOKEN_COOKIE_NAME)) {
                if (StringUtils.isEmpty(cookie.getValue())) {
                    return null;
                } else {
                    found = cookie;
                }
            }
        }

        return found;
    }

    private String getRequiredInitParameter(FilterConfig filterConfig, String parameterName) {
        String parameter = filterConfig.getInitParameter(parameterName);
        checkNotNull(parameter, "FilterConfig init parameter '" + parameterName + "' was not set.");
        return parameter;
    }
}
