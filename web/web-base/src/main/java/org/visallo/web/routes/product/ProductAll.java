package org.visallo.web.routes.product;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.core.bootstrap.InjectHelper;
import org.visallo.core.config.Configuration;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.model.workspace.product.Product;
import org.visallo.core.model.workspace.product.WorkProductService;
import org.visallo.core.user.User;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.web.clientapi.model.ClientApiProducts;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class ProductAll implements ParameterizedHandler {
    private final WorkspaceRepository workspaceRepository;
    private final Configuration configuration;

    @Inject
    public ProductAll(
            WorkspaceRepository workspaceRepository,
            Configuration configuration
    ) {
        this.workspaceRepository = workspaceRepository;
        this.configuration = configuration;
    }

    @Handle
    public ClientApiProducts handle(
            @ActiveWorkspaceId String workspaceId,
            User user
    ) throws Exception {
        Collection<Product> products = workspaceRepository.findAllProductsForWorkspace(workspaceId, user);
        if (products == null) {
            throw new VisalloResourceNotFoundException("Could not find products for workspace " + workspaceId);
        }

        String lastActiveProductId = workspaceRepository.getLastActiveProductId(workspaceId, user);

        List<String> types = InjectHelper.getInjectedServices(WorkProductService.class, configuration).stream()
                .map(WorkProductService::getKind)
                .collect(Collectors.toList());

        ClientApiProducts clientApiProducts = ClientApiConverter.toClientApiProducts(types, products);
        clientApiProducts.products
                .forEach(product -> product.active = product.id.equals(lastActiveProductId));
        return clientApiProducts;
    }
}
