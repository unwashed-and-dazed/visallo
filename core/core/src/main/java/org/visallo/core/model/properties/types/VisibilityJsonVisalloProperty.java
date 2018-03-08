package org.visallo.core.model.properties.types;

import org.visallo.web.clientapi.model.VisibilityJson;

public class VisibilityJsonVisalloProperty extends ClientApiSingleValueVisalloProperty<VisibilityJson> {
    public VisibilityJsonVisalloProperty(String key) {
        super(key, VisibilityJson.class);
    }
}
