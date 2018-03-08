package org.visallo.core.model.workspace.product;

import org.visallo.web.clientapi.model.ClientApiObject;

public class WorkProductVertex implements ClientApiObject {
    private String id;
    private boolean visible = true;
    private String title;
    private String type;
    private boolean unauthorized;
    private boolean ancillary = false;

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setUnauthorized(boolean unauthorized) {
        this.unauthorized = unauthorized;
    }

    public boolean isUnauthorized() {
        return unauthorized;
    }

    public boolean isAncillary() {
        return ancillary;
    }

    public void setAncillary(boolean ancillary) {
        this.ancillary = ancillary;
    }
}
