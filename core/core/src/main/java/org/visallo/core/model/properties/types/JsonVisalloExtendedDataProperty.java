package org.visallo.core.model.properties.types;

import org.json.JSONObject;
import org.visallo.core.util.JSONUtil;

public class JsonVisalloExtendedDataProperty extends VisalloExtendedData<JSONObject, String> {
    public JsonVisalloExtendedDataProperty(String tableName, String columnName) {
        super(tableName, columnName);
    }

    @Override
    public String rawToGraph(JSONObject value) {
        return value.toString();
    }

    @Override
    public JSONObject graphToRaw(Object value) {
        if (value == null) {
            return null;
        }
        return JSONUtil.parse(value.toString());
    }
}
