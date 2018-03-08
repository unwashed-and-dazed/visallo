package org.visallo.web.auth;

import com.google.inject.Injector;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.visallo.core.bootstrap.InjectHelper;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.user.User;
import org.visallo.vertexium.model.user.InMemoryUser;
import org.visallo.web.CurrentUser;

import javax.crypto.SecretKey;
import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.visallo.core.config.Configuration.*;

@RunWith(MockitoJUnitRunner.class)
public class AuthTokenFilterTest {

    private static final String EXPIRATION = "60";
    private static final String EXPIRATION_TOLERANCE = "5";
    private static final String PASSWORD = "password";
    private static final String SALT = "salt";

    @Mock
    private FilterConfig filterConfig;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Injector injector;

    private AuthTokenFilter filter;

    private User user = new InMemoryUser("user123");

    @Before
    public void before() throws ServletException {
        when(filterConfig.getInitParameter(AUTH_TOKEN_PASSWORD)).thenReturn(PASSWORD);
        when(filterConfig.getInitParameter(AUTH_TOKEN_SALT)).thenReturn(SALT);
        when(filterConfig.getInitParameter(AUTH_TOKEN_EXPIRATION_IN_MINS)).thenReturn(EXPIRATION);
        when(filterConfig.getInitParameter(AUTH_TOKEN_EXPIRATION_TOLERANCE_IN_SECS)).thenReturn(EXPIRATION_TOLERANCE);
        when(injector.getInstance(UserRepository.class)).thenReturn(userRepository);
        InjectHelper.setInjector(injector);
        filter = new AuthTokenFilter();
        filter.init(filterConfig);
    }

    @Test
    public void testNoTokenCookiePresentDoesNotSetToken() throws IOException, ServletException {
        when(request.getCookies()).thenReturn(new Cookie[0]);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(eq(request), any(HttpServletResponse.class));
        verify(response, never()).addCookie(any(Cookie.class));
    }

    @Test
    public void testValidInboundTokenSetsCurrentUser() throws Exception {
        AuthToken token = getToken(user.getUserId(), new Date(System.currentTimeMillis() + 10000));
        Cookie cookie = getTokenCookie(token);
        when(request.getCookies()).thenReturn(new Cookie[] { cookie });
        when(userRepository.findById(token.getUserId())).thenReturn(user);
        filter.doFilter(request, response, chain);
        verify(request).setAttribute(CurrentUser.CURRENT_USER_REQ_ATTR_NAME, user);
        verify(chain).doFilter(eq(request), any(HttpServletResponse.class));
    }

    @Test
    public void testExpiredTokenDoesNotSetCurrentUser() throws Exception {
        AuthToken token = getToken(user.getUserId(), new Date(System.currentTimeMillis() - 10000));
        Cookie cookie = getTokenCookie(token);
        when(request.getCookies()).thenReturn(new Cookie[] { cookie });
        when(userRepository.findById(token.getUserId())).thenReturn(user);
        filter.doFilter(request, response, chain);
        verify(request, never()).setAttribute(CurrentUser.CURRENT_USER_REQ_ATTR_NAME, user);
        verify(chain).doFilter(eq(request), any(HttpServletResponse.class));
    }

    @Test
    public void testExpiredTokenWithinToleranceDoesSetCurrentUser() throws Exception {
        AuthToken token = getToken(user.getUserId(), new Date(System.currentTimeMillis() - 2000));
        Cookie cookie = getTokenCookie(token);
        when(request.getCookies()).thenReturn(new Cookie[] { cookie });
        when(userRepository.findById(token.getUserId())).thenReturn(user);
        filter.doFilter(request, response, chain);
        verify(request).setAttribute(CurrentUser.CURRENT_USER_REQ_ATTR_NAME, user);
        verify(chain).doFilter(eq(request), any(HttpServletResponse.class));
    }

    @Test
    public void testExpiredTokenRemovesTokenCookie() throws Exception {
        AuthToken token = getToken(user.getUserId(), new Date(System.currentTimeMillis() - 10000));
        Cookie cookie = getTokenCookie(token);
        when(request.getCookies()).thenReturn(new Cookie[] { cookie });
        when(response.isCommitted()).thenReturn(false);
        filter.doFilter(request, response, chain);
        verify(response).addCookie(argThat(new ArgumentMatcher<Cookie>() {
            @Override
            public boolean matches(Object c) {
                Cookie right = (Cookie) c;
                return right.getName().equals(cookie.getName())
                        && right.getMaxAge() == 0
                        && right.getValue() == null;
            }
        }));
        verify(chain).doFilter(eq(request), any(HttpServletResponse.class));
    }

    @Test
    public void testCurrentUserSetCausesTokenCookieToBeSet() throws Exception {
        when(request.getCookies()).thenReturn(new Cookie[0]);
        when(response.isCommitted()).thenReturn(false);

        FilterChain testChain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
                when(request.getAttribute(CurrentUser.CURRENT_USER_REQ_ATTR_NAME)).thenReturn(user);
                response.getWriter();
            }
        };

        filter.doFilter(request, response, testChain);

        verify(response).addCookie(argThat(new ArgumentMatcher<Cookie>() {
            @Override
            public boolean matches(Object c) {
                try {
                    Cookie cookie = (Cookie) c;
                    SecretKey key = AuthToken.generateKey(PASSWORD, SALT);
                    AuthToken token = AuthToken.parse(cookie.getValue(), key);
                    return token.getUserId().equals(user.getUserId());
                } catch (Exception e) {
                    fail("token signing failed: " + e);
                }
                return false;
            }
        }));
    }

    @Test
    public void testTokenCookieCloseToExpirationGetsReset() throws Exception {
        AuthToken token = getToken(user.getUserId(), new Date(System.currentTimeMillis() + 60000));
        Cookie cookie = getTokenCookie(token);
        when(request.getCookies()).thenReturn(new Cookie[] { cookie });
        when(userRepository.findById(token.getUserId())).thenReturn(user);
        when(request.getAttribute(CurrentUser.CURRENT_USER_REQ_ATTR_NAME)).thenReturn(user);

        when(response.isCommitted()).thenReturn(false);

        FilterChain testChain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
                response.getWriter();
            }
        };

        filter.doFilter(request, response, testChain);

        verify(response).addCookie(argThat(new ArgumentMatcher<Cookie>() {
            @Override
            public boolean matches(Object c) {
                try {
                    Cookie cookie = (Cookie) c;
                    SecretKey key = AuthToken.generateKey(PASSWORD, SALT);
                    AuthToken token = AuthToken.parse(cookie.getValue(), key);
                    return token.getUserId().equals(user.getUserId());
                } catch (Exception e) {
                    fail("token signing failed: " + e);
                }
                return false;
            }
        }));
    }

    @Test
    public void testTokenSignatureFailureSendsError() throws Exception {
        AuthToken token = getToken(user.getUserId(), new Date(System.currentTimeMillis() + 10000));
        Cookie cookie = getTokenCookie(token.serialize() + "a", token.getExpiration());
        when(request.getCookies()).thenReturn(new Cookie[] { cookie });
        when(userRepository.findById(token.getUserId())).thenReturn(user);
        filter.doFilter(request, response, chain);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(request, never()).setAttribute(CurrentUser.CURRENT_USER_REQ_ATTR_NAME, user);
        verify(chain, never()).doFilter(eq(request), any(HttpServletResponse.class));
    }

    private AuthToken getToken(String userid, Date expiration) throws InvalidKeySpecException, NoSuchAlgorithmException {
        SecretKey key = AuthToken.generateKey(PASSWORD, SALT);
        return new AuthToken(userid, key, expiration);
    }

    private Cookie getTokenCookie(AuthToken token) throws AuthTokenException {
        return getTokenCookie(token.serialize(), token.getExpiration());
    }

    private Cookie getTokenCookie(String value, Date expiration) {
        Cookie cookie = new Cookie(AuthTokenFilter.TOKEN_COOKIE_NAME, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setMaxAge((int) ((expiration.getTime() - System.currentTimeMillis()) / 1000));
        return cookie;
    }
}
