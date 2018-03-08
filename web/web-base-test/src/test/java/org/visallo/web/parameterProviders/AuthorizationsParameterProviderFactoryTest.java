package org.visallo.web.parameterProviders;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.vertexium.Authorizations;
import org.vertexium.inmemory.InMemoryAuthorizations;
import org.visallo.core.exception.VisalloAccessDeniedException;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.user.User;
import org.visallo.vertexium.model.user.InMemoryUser;
import org.visallo.web.CurrentUser;

import javax.servlet.http.HttpServletRequest;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizationsParameterProviderFactoryTest {
    @Mock
    private HttpServletRequest request;

    @Mock
    private AuthorizationRepository authorizationRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    private User testUser = new InMemoryUser("user123");

    @Test
    public void testGetAuthorizations() {
        Authorizations authorizations = new InMemoryAuthorizations("a", "b", "workspace123");

        when(request.getAttribute(eq(VisalloBaseParameterProvider.WORKSPACE_ID_ATTRIBUTE_NAME))).thenReturn("workspace123");
        when(request.getAttribute(CurrentUser.CURRENT_USER_REQ_ATTR_NAME)).thenReturn(testUser);
        when(authorizationRepository.getGraphAuthorizations(eq(testUser), eq("workspace123"))).thenReturn(authorizations);
        when(workspaceRepository.hasReadPermissions(eq("workspace123"), eq(testUser))).thenReturn(true);

        Authorizations auth = AuthorizationsParameterProviderFactory.getAuthorizations(
                request,
                authorizationRepository,
                workspaceRepository
        );
        assertArrayEquals(new String[]{"a", "b", "workspace123"}, auth.getAuthorizations());
    }

    @Test
    public void testGetAuthorizationsNoWorkspaceAccess() {
        when(request.getAttribute(eq(VisalloBaseParameterProvider.WORKSPACE_ID_ATTRIBUTE_NAME))).thenReturn("workspace123");
        when(request.getAttribute(CurrentUser.CURRENT_USER_REQ_ATTR_NAME)).thenReturn(testUser);
        when(workspaceRepository.hasReadPermissions(eq("workspace123"), eq(testUser))).thenReturn(false);
        try {
            AuthorizationsParameterProviderFactory.getAuthorizations(
                    request,
                    authorizationRepository,
                    workspaceRepository
            );
            fail("expected exception");
        } catch (VisalloAccessDeniedException ex) {
            assertTrue(ex.getMessage().contains("workspace123"));
        }
    }

    @Test
    public void testGetAuthorizationsNoWorkspace() {
        Authorizations authorizations = new InMemoryAuthorizations("a", "b");

        when(request.getAttribute(eq(VisalloBaseParameterProvider.WORKSPACE_ID_ATTRIBUTE_NAME))).thenReturn(null);
        when(authorizationRepository.getGraphAuthorizations(eq(testUser))).thenReturn(authorizations);
        when(request.getAttribute(CurrentUser.CURRENT_USER_REQ_ATTR_NAME)).thenReturn(testUser);
        Authorizations auth = AuthorizationsParameterProviderFactory.getAuthorizations(
                request,
                authorizationRepository,
                workspaceRepository
        );
        assertArrayEquals(new String[]{"a", "b"}, auth.getAuthorizations());
    }

    @Test
    public void testGetAuthorizationsNoUser() {
        when(request.getAttribute(CurrentUser.CURRENT_USER_REQ_ATTR_NAME)).thenReturn(null);
        Authorizations auth = AuthorizationsParameterProviderFactory.getAuthorizations(
                request,
                authorizationRepository,
                workspaceRepository
        );
        assertNull("expected null authorizations", auth);
        verify(authorizationRepository, never()).getGraphAuthorizations(any());
        verify(authorizationRepository, never()).getGraphAuthorizations(any(), any());
    }
}