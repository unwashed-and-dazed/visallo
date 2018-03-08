package org.visallo.core.model.workspace.product;

import org.json.JSONObject;
import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.vertexium.Vertex;
import org.vertexium.Visibility;
import org.visallo.core.model.graph.GraphUpdateContext;
import org.visallo.core.user.User;

public interface WorkProductService {

    /**
     * Get custom extended data from the work product. This does not include the extended data stored on the
     * product itself.
     */
    WorkProductExtendedData getExtendedData(
            Graph graph,
            Vertex workspaceVertex,
            Vertex productVertex,
            GetExtendedDataParams params,
            User user,
            Authorizations authorizations
    );

    String getKind();
}
