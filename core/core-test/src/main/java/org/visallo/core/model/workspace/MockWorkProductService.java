package org.visallo.core.model.workspace;

import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.vertexium.Vertex;
import org.visallo.core.model.workspace.product.GetExtendedDataParams;
import org.visallo.core.model.workspace.product.WorkProductExtendedData;
import org.visallo.core.model.workspace.product.WorkProductService;
import org.visallo.core.user.User;

public class MockWorkProductService implements WorkProductService {
    public static final String KIND = "org.visallo.core.model.workspace.MockWorkProduct";

    @Override
    public WorkProductExtendedData getExtendedData(
            Graph graph,
            Vertex workspaceVertex,
            Vertex productVertex,
            GetExtendedDataParams params,
            User user,
            Authorizations authorizations
    ) {
        return new WorkProductExtendedData();
    }

    @Override
    public String getKind() {
        return KIND;
    }
}
