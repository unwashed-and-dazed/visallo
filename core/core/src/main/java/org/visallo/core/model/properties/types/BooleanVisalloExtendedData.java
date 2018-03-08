package org.visallo.core.model.properties.types;

import org.vertexium.ExtendedDataRow;

public class BooleanVisalloExtendedData extends IdentityVisalloExtendedData<Boolean> {
    public BooleanVisalloExtendedData(String tableName, String propertyName) {
        super(tableName, propertyName);
    }

    public boolean getValue(ExtendedDataRow row, boolean defaultValue) {
        Boolean nullable = getValue(row);
        if (nullable == null) {
            return defaultValue;
        }
        return nullable;
    }
}
