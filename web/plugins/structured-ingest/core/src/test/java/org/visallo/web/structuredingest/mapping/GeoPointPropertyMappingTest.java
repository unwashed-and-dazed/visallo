package org.visallo.web.structuredingest.mapping;

import org.json.JSONObject;
import org.junit.Test;
import org.vertexium.type.GeoPoint;
import org.visallo.web.structuredingest.core.util.mapping.GeoPointPropertyMapping;
import org.visallo.web.structuredingest.core.util.mapping.PropertyMapping;

import java.util.*;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNull;
import static org.visallo.web.structuredingest.mapping.MappingTestHelpers.createIndexedMap;

public class GeoPointPropertyMappingTest {
    private static final double DELTA = 1e-6;

    private final double expectedLat = 39.062139D;
    private final double expectedLon = -77.465943D;
    private final Map<String, Object> DECIMAL_ROW = createIndexedMap("39.062139, -77.465943", "39.062139", "-77.465943", "39", "-77");
    private final Map<String, Object> DM_ROW = createIndexedMap(new String[] { "39° 3.72834', -77° 27.95657'", "39° 3.72834'", "-77° 27.95657'"});
    private final Map<String, Object> DMS_ROW = createIndexedMap(new String[] { "39° 3' 43.7004\", -77° 27' 57.3942\"", "39° 3' 43.7004\"", "-77° 27' 57.3942\""});

    @Test
    public void testSingleColumn() throws Exception {
        GeoPointPropertyMapping propertyMapping = new GeoPointPropertyMapping(null, null, buildJsonPropertyMapping("DECIMAL", 0));
        GeoPoint result = (GeoPoint)propertyMapping.decodeValue(DECIMAL_ROW);

        assertEquals(expectedLat, result.getLatitude(), DELTA);
        assertEquals(expectedLon, result.getLongitude(), DELTA);
    }

    @Test
    public void testTwoColumns() throws Exception {
        GeoPointPropertyMapping propertyMapping = new GeoPointPropertyMapping(null, null, buildJsonPropertyMapping("DECIMAL", 1));
        GeoPoint result = (GeoPoint)propertyMapping.decodeValue(DECIMAL_ROW);

        assertEquals(expectedLat, result.getLatitude(), DELTA);
        assertEquals(expectedLon, result.getLongitude(), DELTA);
    }

    @Test
    public void testTwoColumnsWithOneBlank() throws Exception {
        GeoPointPropertyMapping propertyMapping = new GeoPointPropertyMapping(null, null, buildJsonPropertyMapping("DECIMAL", 1));

        Map<String, Object> input = new HashMap<>(DECIMAL_ROW);
        input.put(2 + "", "");

        assertNull(propertyMapping.decodeValue(input));
    }

    @Test
    public void testIntegerValues() throws Exception {
        GeoPointPropertyMapping propertyMapping = new GeoPointPropertyMapping(null, null, buildJsonPropertyMapping("DECIMAL", 3));
        GeoPoint result = (GeoPoint)propertyMapping.decodeValue(DECIMAL_ROW);

        assertEquals((int) expectedLat, (int)result.getLatitude());
        assertEquals((int) expectedLon, (int)result.getLongitude());
    }

    @Test
    public void testDegreesMinutes() throws Exception {
        GeoPointPropertyMapping propertyMapping = new GeoPointPropertyMapping(null, null, buildJsonPropertyMapping("DEGREES_DECIMAL_MINUTES", 0));
        GeoPoint result = (GeoPoint)propertyMapping.decodeValue(DM_ROW);

        assertEquals(expectedLat, result.getLatitude(), DELTA);
        assertEquals(expectedLon, result.getLongitude(), DELTA);
    }

    @Test
    public void testDegreesMinutesSeconds() throws Exception {
        GeoPointPropertyMapping propertyMapping = new GeoPointPropertyMapping(null, null, buildJsonPropertyMapping("DEGREES_MINUTES_SECONDS", 0));
        GeoPoint result = (GeoPoint)propertyMapping.decodeValue(DMS_ROW);

        assertEquals(expectedLat, result.getLatitude(), DELTA);
        assertEquals(expectedLon, result.getLongitude(), DELTA);
    }

    private JSONObject buildJsonPropertyMapping(String format, int startColumn) {
        JSONObject jsonProperyMapping = new JSONObject();
        jsonProperyMapping.put(PropertyMapping.PROPERTY_MAPPING_NAME_KEY, "JUNIT");
        if (startColumn == 0) {
            jsonProperyMapping.put(PropertyMapping.PROPERTY_MAPPING_KEY_KEY, 0);
        } else if (startColumn == 1) {
            jsonProperyMapping.put(GeoPointPropertyMapping.PROPERTY_MAPPING_COLUMN_LAT_KEY, 1);
            jsonProperyMapping.put(GeoPointPropertyMapping.PROPERTY_MAPPING_COLUMN_LON_KEY, 2);
        } else if (startColumn == 3) {
            jsonProperyMapping.put(GeoPointPropertyMapping.PROPERTY_MAPPING_COLUMN_LAT_KEY, 3);
            jsonProperyMapping.put(GeoPointPropertyMapping.PROPERTY_MAPPING_COLUMN_LON_KEY, 4);
        }
        jsonProperyMapping.put(GeoPointPropertyMapping.PROPERTY_MAPPING_FORMAT_KEY, format);
        return jsonProperyMapping;
    }
}
