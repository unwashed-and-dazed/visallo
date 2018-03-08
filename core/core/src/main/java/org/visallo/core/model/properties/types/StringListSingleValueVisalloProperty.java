package org.visallo.core.model.properties.types;

import org.json.JSONArray;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.util.JSONUtil;

import java.util.List;

public class StringListSingleValueVisalloProperty extends SingleValueVisalloProperty<List<String>, String> {
    public StringListSingleValueVisalloProperty(String key) {
        super(key);
    }

    @Override
    public String wrap(List<String> value) {
        return new JSONArray(value).toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> unwrap(Object value) {
        if (value == null) {
            return null;
        }

        List<String> valueList;
        if (value instanceof String) {
            valueList = JSONUtil.toStringList(JSONUtil.parseArray(value.toString()));
        } else if (value instanceof List) {
            valueList = (List<String>) value;
        } else {
            throw new VisalloException("Could not unwrap type: " + value.getClass().getName());
        }

        return valueList;
    }
}
