package org.visallo.core.model.properties.types;

import org.vertexium.Element;
import org.visallo.core.model.graph.ElementUpdateContext;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

/**
 * A VisalloProperty that converts Dates to an appropriate value for
 * storage in Vertexium.
 */
public class DateVisalloProperty extends IdentityVisalloProperty<Date> {
    public DateVisalloProperty(String key) {
        super(key);
    }

    public <T extends Element> void updateProperty(
            ElementUpdateContext<T> ctx,
            String propertyKey,
            OffsetDateTime newValue,
            PropertyMetadata metadata
    ) {
        updateProperty(ctx, propertyKey, newValue, metadata, null);
    }

    public <T extends Element> void updateProperty(
            ElementUpdateContext<T> ctx,
            String propertyKey,
            OffsetDateTime newValue,
            PropertyMetadata metadata,
            Long timestamp
    ) {
        Date date = newValue == null ? null : Date.from(newValue.toInstant());
        updateProperty(ctx, propertyKey, date, metadata, timestamp);
    }

    public <T extends Element> void updateProperty(
            ElementUpdateContext<T> ctx,
            String propertyKey,
            ZonedDateTime newValue,
            PropertyMetadata metadata
    ) {
        updateProperty(ctx, propertyKey, newValue, metadata, null);
    }

    public <T extends Element> void updateProperty(
            ElementUpdateContext<T> ctx,
            String propertyKey,
            ZonedDateTime newValue,
            PropertyMetadata metadata,
            Long timestamp
    ) {
        Date date = newValue == null ? null : Date.from(newValue.toInstant());
        updateProperty(ctx, propertyKey, date, metadata, timestamp);
    }

    /**
     * Updates the element with the new property value if the property value is newer than the existing property value
     * or the update does not have an existing element (for example a new element or a blind write mutation)
     */
    public <T extends Element> void updatePropertyIfValueIsNewer(
            ElementUpdateContext<T> ctx,
            String propertyKey,
            Date newValue,
            PropertyMetadata metadata,
            Long timestamp
    ) {
        if (isDateNewer(ctx.getElement(), propertyKey, newValue)) {
            updateProperty(ctx, propertyKey, newValue, metadata, timestamp);
        }
    }

    /**
     * Updates the element with the new property value if the property value is newer than the existing property value
     * or the update does not have an existing element (for example a new element or a blind write mutation)
     */
    public <T extends Element> void updatePropertyIfValueIsNewer(
            ElementUpdateContext<T> ctx,
            String propertyKey,
            Date newValue,
            PropertyMetadata metadata
    ) {
        if (isDateNewer(ctx.getElement(), propertyKey, newValue)) {
            updateProperty(ctx, propertyKey, newValue, metadata);
        }
    }

    /**
     * Updates the element with the new property value if the property value is older than the existing property value
     * or the update does not have an existing element (for example a new element or a blind write mutation)
     */
    public <T extends Element> void updatePropertyIfValueIsOlder(
            ElementUpdateContext<T> ctx,
            String propertyKey,
            Date newValue,
            PropertyMetadata metadata,
            Long timestamp
    ) {
        if (isDateOlder(ctx.getElement(), propertyKey, newValue)) {
            updateProperty(ctx, propertyKey, newValue, metadata, timestamp);
        }
    }

    /**
     * Updates the element with the new property value if the property value is older than the existing property value
     * or the update does not have an existing element (for example a new element or a blind write mutation)
     */
    public <T extends Element> void updatePropertyIfValueIsOlder(
            ElementUpdateContext<T> ctx,
            String propertyKey,
            Date newValue,
            PropertyMetadata metadata
    ) {
        if (isDateOlder(ctx.getElement(), propertyKey, newValue)) {
            updateProperty(ctx, propertyKey, newValue, metadata);
        }
    }

    private <T extends Element> boolean isDateNewer(T element, String propertyKey, Date newValue) {
        if (element == null) {
            return true;
        }
        Date existingValue = getPropertyValue(element, propertyKey);
        if (existingValue == null) {
            return true;
        }
        return existingValue.compareTo(newValue) < 0;
    }

    private <T extends Element> boolean isDateOlder(T element, String propertyKey, Date newValue) {
        if (element == null) {
            return true;
        }
        Date existingValue = getPropertyValue(element, propertyKey);
        if (existingValue == null) {
            return true;
        }
        return existingValue.compareTo(newValue) > 0;
    }

    public ZonedDateTime getPropertyValueDateTimeUtc(Element element, String propertyKey) {
        return getPropertyValueDateTime(element, propertyKey, ZoneOffset.UTC);
    }

    public ZonedDateTime getPropertyValueDateTime(Element element, String propertyKey, ZoneId zoneId) {
        Date value = getPropertyValue(element, propertyKey);
        if (value == null) {
            return null;
        }
        return ZonedDateTime.ofInstant(value.toInstant(), zoneId);
    }

    public ZonedDateTime getFirstPropertyValueDateTimeUtc(Element element) {
        return getFirstPropertyValueDateTime(element, ZoneOffset.UTC);
    }

    public ZonedDateTime getFirstPropertyValueDateTime(Element element, ZoneId zoneId) {
        Date value = getFirstPropertyValue(element);
        if (value == null) {
            return null;
        }
        return ZonedDateTime.ofInstant(value.toInstant(), zoneId);
    }
}
