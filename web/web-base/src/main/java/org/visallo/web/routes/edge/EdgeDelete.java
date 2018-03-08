package org.visallo.web.routes.edge;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.vertexium.*;
import org.visallo.core.exception.VisalloAccessDeniedException;
import org.visallo.core.model.workQueue.Priority;
import org.visallo.core.model.workspace.WorkspaceHelper;
import org.visallo.core.security.ACLProvider;
import org.visallo.core.user.User;
import org.visallo.core.util.SandboxStatusUtil;
import org.visallo.web.VisalloResponse;
import org.visallo.web.clientapi.model.ClientApiSuccess;
import org.visallo.web.clientapi.model.SandboxStatus;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

@Singleton
public class EdgeDelete implements ParameterizedHandler {
    private final Graph graph;
    private final WorkspaceHelper workspaceHelper;
    private final ACLProvider aclProvider;

    @Inject
    public EdgeDelete(
            final Graph graph,
            final WorkspaceHelper workspaceHelper,
            final ACLProvider aclProvider
            ) {
        this.graph = graph;
        this.workspaceHelper = workspaceHelper;
        this.aclProvider = aclProvider;
    }

    @Handle
    public ClientApiSuccess handle(
            @Required(name = "edgeId") String edgeId,
            @ActiveWorkspaceId String workspaceId,
            User user,
            Authorizations authorizations
    ) throws Exception {
        Edge edge = graph.getEdge(edgeId, authorizations);
        if (!aclProvider.canDeleteElement(edge, user, workspaceId)) {
            throw new VisalloAccessDeniedException("Edge " + edgeId + " is not deleteable", user, edge.getId());
        }

        Vertex outVertex = edge.getVertex(Direction.OUT, authorizations);
        Vertex inVertex = edge.getVertex(Direction.IN, authorizations);

        SandboxStatus sandboxStatus = SandboxStatusUtil.getSandboxStatus(edge, workspaceId);

        boolean isPublicEdge = sandboxStatus == SandboxStatus.PUBLIC;

        workspaceHelper.deleteEdge(workspaceId, edge, outVertex, inVertex, isPublicEdge, Priority.HIGH, authorizations, user);

        return VisalloResponse.SUCCESS;
    }
}
