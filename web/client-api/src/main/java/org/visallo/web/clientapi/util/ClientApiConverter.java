package org.visallo.web.clientapi.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.visallo.web.clientapi.model.DirectoryEntity;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.io.IOException;
import java.util.*;

public class ClientApiConverter {
    public static Object toClientApiValue(Object value) {
        if (value instanceof JSONArray) {
            return toClientApiValue((JSONArray) value);
        } else if (value instanceof JSONObject) {
            return toClientApiValueInternal((JSONObject) value);
        } else if (JSONObject.NULL.equals(value)) {
            return null;
        } else if (value instanceof String) {
            return toClientApiValue((String) value);
        } else if (value instanceof Date) {
            return toClientApiValue(((Date) value).getTime());
        }
        return value;
    }

    public static Object toClientApiMetadataValue(Object value) {
        if (value instanceof String) {
            return toClientApiMetadataValue((String) value);
        } else {
            return toClientApiValue(value);
        }
    }

    private static List<Object> toClientApiValue(JSONArray json) {
        List<Object> result = new ArrayList<Object>();
        for (int i = 0; i < json.length(); i++) {
            Object obj = json.get(i);
            result.add(toClientApiValue(obj));
        }
        return result;
    }

    private static Object toClientApiValue(String value) {
        return value.trim();
    }

    private static Object toClientApiMetadataValue(String value) {
        try {
            String valueString = value;
            valueString = valueString.trim();
            if (valueString.startsWith("{") && valueString.endsWith("}")) {
                return toClientApiValue((Object) new JSONObject(valueString));
            }
        } catch (Exception ex) {
            // ignore this exception it just means the string wasn't really json
        }
        return value;
    }

    private static Object toClientApiValueInternal(JSONObject json) {
        if (json.length() == 2 && json.has("source") && json.has("workspaces")) {
            VisibilityJson visibilityJson = new VisibilityJson();
            visibilityJson.setSource(json.getString("source"));
            JSONArray workspacesJson = json.getJSONArray("workspaces");
            for (int i = 0; i < workspacesJson.length(); i++) {
                visibilityJson.addWorkspace(workspacesJson.getString(i));
            }
            return visibilityJson;
        }
        return toClientApiValue(json);
    }

    public static Map<String, Object> toClientApiValue(JSONObject json) {
        Map<String, Object> result = new HashMap<String, Object>();
        for (Object key : json.keySet()) {
            String keyStr = (String) key;
            result.put(keyStr, toClientApiValue(json.get(keyStr)));
        }
        return result;
    }

    public static Object fromClientApiValue(Object obj) {
        if (obj instanceof Map) {
            Map map = (Map) obj;
            if (VisibilityJson.isVisibilityJson(map)) {
                return VisibilityJson.fromMap(map);
            }
            if (DirectoryEntity.isEntity(map)) {
                return DirectoryEntity.fromMap(map);
            }
        }
        return obj;
    }

    public static String clientApiToString(Object o) {
        if (o == null) {
            throw new RuntimeException("o cannot be null.");
        }
        try {
            return ObjectMapperFactory.getInstance().writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not convert object '" + o.getClass().getName() + "' to string", e);
        }
    }

    public static <T> T toClientApi(JSONObject json, Class<T> clazz) {
        return toClientApi(json.toString(), clazz);
    }

    public static <T> T toClientApi(String str, Class<T> clazz) {
        try {
            return ObjectMapperFactory.getInstance().readValue(str, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Could not parse '" + str + "' to class '" + clazz.getName() + "'", e);
        }
    }

    public static <T> Map<String, T> toClientApiMap(String str, Class<T> clazz) {
        try {
            MapType typeRef = TypeFactory.defaultInstance().constructMapType(HashMap.class, String.class, clazz);
            return ObjectMapperFactory.getInstance().readValue(str, typeRef);
        } catch (IOException e) {
            throw new RuntimeException("Could not parse '" + str + "' to class '" + clazz.getName() + "'", e);
        }
    }
}
