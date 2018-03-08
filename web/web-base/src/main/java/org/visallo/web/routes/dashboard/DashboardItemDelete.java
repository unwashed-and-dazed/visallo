package org.visallo.web.routes.dashboard;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.workspace.DashboardItem;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.user.User;
import org.visallo.web.VisalloResponse;
import org.visallo.web.clientapi.model.ClientApiSuccess;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

@Singleton
public class DashboardItemDelete implements ParameterizedHandler {
    private final WorkspaceRepository workspaceRepository;

    @Inject
    public DashboardItemDelete(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Handle
    public ClientApiSuccess handle(
            @Required(name = "dashboardItemId") String dashboardItemId,
            @ActiveWorkspaceId String workspaceId,
            User user
    ) throws Exception {
        DashboardItem dashboardItem = workspaceRepository.findDashboardItemById(workspaceId, dashboardItemId, user);
        if (dashboardItem == null) {
            throw new VisalloResourceNotFoundException("Could not find dashboard item with id " + dashboardItemId);
        }

        workspaceRepository.deleteDashboardItem(workspaceId, dashboardItemId, user);
        return VisalloResponse.SUCCESS;
    }
}
