package org.visallo.core.model.properties.types;

public class DerivedFrom {
    private String propertyKey;
    private String propertyName;

    public DerivedFrom(String propertyKey,
                       String propertyName) {
        this.propertyKey = propertyKey;
        this.propertyName = propertyName;
    }

    public String getPropertyKey() {
        return propertyKey;
    }

    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public String toString() {
        return "propertyKey: " + propertyKey + ", propertyName: " + propertyName;
    }
}
