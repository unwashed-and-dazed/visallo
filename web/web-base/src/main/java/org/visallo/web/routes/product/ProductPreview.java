package org.visallo.web.routes.product;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.user.User;
import org.visallo.web.VisalloResponse;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;

@Singleton
public class ProductPreview implements ParameterizedHandler {
    private final WorkspaceRepository workspaceRepository;

    @Inject
    public ProductPreview(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }


    @Handle
    public void handle(
            @Required(name = "productId") String productId,
            @ActiveWorkspaceId String workspaceId,
            User user,
            VisalloResponse response
    ) throws Exception {
        try (InputStream preview = workspaceRepository.getProductPreviewById(workspaceId, productId, user)) {
            if (preview == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                response.write(preview);
            }
        }
    }
}
