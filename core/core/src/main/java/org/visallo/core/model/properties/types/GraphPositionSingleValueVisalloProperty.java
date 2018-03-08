package org.visallo.core.model.properties.types;

import org.visallo.core.util.JSONUtil;
import org.visallo.web.clientapi.model.GraphPosition;

public class GraphPositionSingleValueVisalloProperty extends SingleValueVisalloProperty<GraphPosition, String> {
    public GraphPositionSingleValueVisalloProperty(String key) {
        super(key);
    }

    @Override
    public String wrap(GraphPosition value) {
        return value.toJSONObject().toString();
    }

    @Override
    public GraphPosition unwrap(Object value) {
        if (value == null) {
            return null;
        }
        return GraphPosition.fromJSONObject(JSONUtil.parse(value.toString()));
    }
}
