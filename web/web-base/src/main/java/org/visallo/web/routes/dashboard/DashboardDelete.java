package org.visallo.web.routes.dashboard;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.workspace.Dashboard;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.user.User;
import org.visallo.web.VisalloResponse;
import org.visallo.web.clientapi.model.ClientApiSuccess;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

@Singleton
public class DashboardDelete implements ParameterizedHandler {
    private final WorkspaceRepository workspaceRepository;

    @Inject
    public DashboardDelete(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Handle
    public ClientApiSuccess handle(
            @Required(name = "dashboardId") String dashboardId,
            @ActiveWorkspaceId String workspaceId,
            User user
    ) throws Exception {

        Dashboard dashboard = workspaceRepository.findDashboardById(workspaceId, dashboardId, user);
        if (dashboard == null) {
            throw new VisalloResourceNotFoundException("Could not find dashboard with id " + dashboardId);
        }

        workspaceRepository.deleteDashboard(workspaceId, dashboardId, user);
        return VisalloResponse.SUCCESS;
    }
}
