package org.visallo.core.model.properties.types;

import org.vertexium.Element;
import org.vertexium.type.GeoShape;

public class GeoShapeVisalloProperty extends IdentityVisalloProperty<GeoShape> {
    public GeoShapeVisalloProperty(String propertyName) {
        super(propertyName);
    }

    public GeoShape getPropertyValue(Element element, String propertyKey, GeoShape defaultValue) {
        GeoShape nullable = getPropertyValue(element, propertyKey);
        if (nullable == null) {
            return defaultValue;
        }
        return nullable;
    }
}
