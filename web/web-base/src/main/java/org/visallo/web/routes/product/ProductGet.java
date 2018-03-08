package org.visallo.web.routes.product;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Optional;
import org.visallo.webster.annotations.Required;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.model.workspace.product.GetExtendedDataParams;
import org.visallo.core.model.workspace.product.Product;
import org.visallo.core.user.User;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.web.clientapi.model.ClientApiProduct;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

@Singleton
public class ProductGet implements ParameterizedHandler {
    private final WorkspaceRepository workspaceRepository;

    @Inject
    public ProductGet(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }


    @Handle
    public ClientApiProduct handle(
            @Required(name = "productId") String productId,
            @Optional(name = "includeExtended", defaultValue = "true") boolean includeExtended,
            @Optional(name = "params") String paramsStr,
            @ActiveWorkspaceId String workspaceId,
            User user
    ) throws Exception {
        GetExtendedDataParams params = paramsStr == null
                ? new GetExtendedDataParams()
                : ClientApiConverter.toClientApi(paramsStr, GetExtendedDataParams.class);
        Product product = workspaceRepository.findProductById(workspaceId, productId, params, includeExtended, user);
        return ClientApiConverter.toClientApiProduct(product);
    }
}
