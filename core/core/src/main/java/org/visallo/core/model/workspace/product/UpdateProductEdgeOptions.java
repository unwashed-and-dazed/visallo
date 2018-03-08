package org.visallo.core.model.workspace.product;

import org.visallo.web.clientapi.model.ClientApiObject;

public class UpdateProductEdgeOptions implements ClientApiObject {
    private boolean isAncillary = false;

    public boolean isAncillary() {
        return isAncillary;
    }

    public void setAncillary(boolean ancillary) {
        isAncillary = ancillary;
    }
}
