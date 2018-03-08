package org.visallo.web.product.graph.model;

import org.visallo.web.clientapi.model.ClientApiObject;

public class RemoveVerticesParams implements ClientApiObject {
    private boolean removeChildren;
    private BroadcastOptions broadcastOptions;

    public boolean isRemoveChildren() {
        return removeChildren;
    }

    public void setRemoveChildren(boolean removeChildren) {
        this.removeChildren = removeChildren;
    }

    public BroadcastOptions getBroadcastOptions() {
        return broadcastOptions;
    }

    public void setBroadcastOptions(BroadcastOptions broadcastOptions) {
        this.broadcastOptions = broadcastOptions;
    }

    public static class BroadcastOptions {
        private boolean preventBroadcastToSourceGuid;

        public boolean isPreventBroadcastToSourceGuid() {
            return preventBroadcastToSourceGuid;
        }

        public void setPreventBroadcastToSourceGuid(boolean preventBroadcastToSourceGuid) {
            this.preventBroadcastToSourceGuid = preventBroadcastToSourceGuid;
        }
    }
}
