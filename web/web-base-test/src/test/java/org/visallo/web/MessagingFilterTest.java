package org.visallo.web;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.user.User;
import org.visallo.vertexium.model.user.InMemoryUser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MessagingFilterTest {
    private MessagingFilter messagingFilter;

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.atmosphere.cpr.AtmosphereRequest request;

    private User user = new InMemoryUser("user123");

    @Before
    public void before() {
        messagingFilter = new MessagingFilter();
        messagingFilter.setUserRepository(userRepository);
        when(request.getAttribute(CurrentUser.CURRENT_USER_REQ_ATTR_NAME)).thenReturn(user);
    }

    @Test
    public void testShouldNotSendSetActiveWorkspaceMessage() {
        JSONObject message = new JSONObject();
        message.put("type", MessagingFilter.TYPE_SET_ACTIVE_WORKSPACE);
        assertFalse(messagingFilter.shouldSendMessage(message, null));
    }

    @Test
    public void testShouldSendMessageSessionNull() {
        JSONObject message = new JSONObject();
        assertFalse(messagingFilter.shouldSendMessage(message, null));
    }

    @Test
    public void testShouldSendMessageSessionNotNull() {
        JSONObject message = new JSONObject();
        assertTrue(messagingFilter.shouldSendMessage(message, request));
    }

    @Test
    public void testShouldSendMessageBasedOnUserPermissions() {
        JSONObject message = new JSONObject("{ permissions: { users: ['user123'] } }");
        assertTrue(messagingFilter.shouldSendMessage(message, request));
    }

    @Test
    public void testShouldNotSendMessageBasedOnUserPermissions() {
        JSONObject message = new JSONObject("{ permissions: { users: ['user456'] } }");
        assertFalse(messagingFilter.shouldSendMessage(message, request));
    }

    @Test
    public void testShouldSendMessageBasedOnWorkspacesPermissions() {
        when(userRepository.getCurrentWorkspaceId("user123")).thenReturn("workspace123");
        JSONObject message = new JSONObject("{ permissions: { workspaces: ['workspace123'] } }");
        assertTrue(messagingFilter.shouldSendMessage(message, request));
    }

    @Test
    public void testShouldNotSendMessageBasedOnWorkspacesPermissions() {
        when(userRepository.getCurrentWorkspaceId("user123")).thenReturn("workspace123");
        JSONObject message = new JSONObject("{ permissions: { workspaces: ['workspace456'] } }");
        assertFalse(messagingFilter.shouldSendMessage(message, request));
    }
}