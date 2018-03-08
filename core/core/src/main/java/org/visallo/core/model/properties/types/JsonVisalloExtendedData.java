package org.visallo.core.model.properties.types;

import org.json.JSONObject;

public class JsonVisalloExtendedData extends VisalloExtendedData<JSONObject, String> {
    public JsonVisalloExtendedData(String tableName, String columnName) {
        super(tableName, columnName);
    }

    @Override
    public String rawToGraph(JSONObject value) {
        return value.toString();
    }

    @Override
    public JSONObject graphToRaw(Object value) {
        return new JSONObject(value.toString());
    }
}
