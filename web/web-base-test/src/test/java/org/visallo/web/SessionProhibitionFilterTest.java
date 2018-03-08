package org.visallo.web;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.visallo.webster.HandlerChain;
import org.visallo.webster.RequestResponseHandler;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

@RunWith(JUnit4.class)
public class SessionProhibitionFilterTest {
    private SessionProhibitionFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @Before
    public void setup() {
        filter = new SessionProhibitionFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetSessionIsProhibited() throws Exception {
        RequestResponseHandler nextHandler = (request, response, chain) -> {
            request.getSession();
        };
        filter.doFilter(request, response, chain(nextHandler));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetSessionWithTrueArgIsProhibited() throws Exception {
        RequestResponseHandler nextHandler = (request, response, chain) -> {
            request.getSession(true);
        };
        filter.doFilter(request, response, chain(nextHandler));
    }

    @Test
    public void testGetSessionWithFalseArgIsNotProhibited() throws Exception {
        RequestResponseHandler nextHandler = (request, response, chain) -> {
            assertNull("Session should return null", request.getSession(false));
        };
        filter.doFilter(request, response, chain(nextHandler));
    }

    @Test
    public void testGetRequestSessionId() throws Exception {
        RequestResponseHandler nextHandler = (request, response, chain) -> {
            assertNull(request.getRequestedSessionId());
        };
        filter.doFilter(request, response, chain(nextHandler));
    }

    @Test
    public void testIsRequestedSessionIdValid() throws Exception {
        RequestResponseHandler nextHandler = (request, response, chain) -> {
            assertFalse(request.isRequestedSessionIdValid());
        };
        filter.doFilter(request, response, chain(nextHandler));
    }

    @Test
    public void testIsRequestedSessionIdFromCookie() throws Exception {
        RequestResponseHandler nextHandler = (request, response, chain) -> {
            assertFalse(request.isRequestedSessionIdFromCookie());
        };
        filter.doFilter(request, response, chain(nextHandler));
    }

    @Test
    public void testIsRequestedSessionIdFromUrl() throws Exception {
        RequestResponseHandler nextHandler = (request, response, chain) -> {
            assertFalse(request.isRequestedSessionIdFromUrl());
        };
        filter.doFilter(request, response, chain(nextHandler));
    }

    @Test
    public void testIsRequestedSessionIdFromURL() throws Exception {
        RequestResponseHandler nextHandler = (request, response, chain) -> {
            assertFalse(request.isRequestedSessionIdFromURL());
        };
        filter.doFilter(request, response, chain(nextHandler));
    }

    @Test(expected = IllegalStateException.class)
    public void testChangeSessionIdIsProhibited() throws Exception {
        RequestResponseHandler nextHandler = (request, response, chain) -> {
            request.changeSessionId();
        };
        filter.doFilter(request, response, chain(nextHandler));
    }

    public FilterChain chain(RequestResponseHandler handler) {
        HandlerChain handlerChain = new HandlerChain(new RequestResponseHandler[] { handler });
        return (request, response) -> {
            try {
                handler.handle((HttpServletRequest) request, (HttpServletResponse) response, handlerChain);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new ServletException(e);
            }
        };
    }
}
