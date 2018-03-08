package org.visallo.core.model.properties.types;

import org.vertexium.Metadata;
import org.vertexium.Visibility;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.user.User;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class PropertyMetadata {
    private static final Visibility DEFAULT_VISIBILITY = new Visibility("");

    private final Date modifiedDate;
    private final User modifiedBy;
    private final Double confidence;
    private final VisibilityJson visibilityJson;
    private final Visibility propertyVisibility;
    private final DerivedFrom derivedFromProperty;
    private final List<AdditionalMetadataItem> additionalMetadataItems = new ArrayList<>();

    /**
     * @param modifiedBy                The user to set as the modifiedBy metadata
     * @param visibilityJson            The visibility json to use in the metadata
     * @param propertyVisibility        The visibility of the property
     */
    public PropertyMetadata(User modifiedBy, VisibilityJson visibilityJson, Visibility propertyVisibility) {
        this(new Date(), modifiedBy, visibilityJson, propertyVisibility);
    }

    public PropertyMetadata(OffsetDateTime modifiedDate, User modifiedBy, VisibilityJson visibilityJson, Visibility visibility) {
        this(Date.from(modifiedDate.toInstant()), modifiedBy, null, visibilityJson, visibility);
    }

    /**
     * @param modifiedDate              The date to use as modifiedDate
     * @param modifiedBy                The user to set as the modifiedBy metadata
     * @param visibilityJson            The visibility json to use in the metadata
     * @param propertyVisibility        The visibility of the property
     */
    public PropertyMetadata(Date modifiedDate, User modifiedBy, VisibilityJson visibilityJson, Visibility propertyVisibility) {
        this(modifiedDate, modifiedBy, null, null, visibilityJson, propertyVisibility);
    }

    /**
     * @param modifiedBy                The user to set as the modifiedBy metadata
     * @param derivedFrom               The property key and name the property was derived from
     * @param visibilityJson            The visibility json to use in the metadata
     * @param propertyVisibility        The visibility of the property
     */
    public PropertyMetadata(User modifiedBy,
                            DerivedFrom derivedFrom,
                            VisibilityJson visibilityJson,
                            Visibility propertyVisibility) {
        this(new Date(), modifiedBy, null, derivedFrom, visibilityJson, propertyVisibility);
    }

    /**
     * @param modifiedDate              The date to use as modifiedDate
     * @param modifiedBy                The user to set as the modifiedBy metadata
     * @param confidence                The confidence metadata value
     * @param visibilityJson            The visibility json to use in the metadata
     * @param propertyVisibility        The visibility of the property
     */
    public PropertyMetadata(
            Date modifiedDate,
            User modifiedBy,
            Double confidence,
            VisibilityJson visibilityJson,
            Visibility propertyVisibility
    ){
        this(new Date(), modifiedBy, confidence, null, visibilityJson, propertyVisibility);
    }
    /**
     * @param modifiedDate              The date to use as modifiedDate
     * @param modifiedBy                The user to set as the modifiedBy metadata
     * @param confidence                The confidence metadata value
     * @param derivedFromProperty      The property name & key the property was derived from
     * @param visibilityJson            The visibility json to use in the metadata
     * @param propertyVisibility        The visibility of the property
     */
    public PropertyMetadata(
            Date modifiedDate,
            User modifiedBy,
            Double confidence,
            DerivedFrom derivedFromProperty,
            VisibilityJson visibilityJson,
            Visibility propertyVisibility
    ) {
        checkNotNull(modifiedDate, "modifiedDate cannot be null");
        checkNotNull(modifiedBy, "modifiedBy cannot be null");
        checkNotNull(visibilityJson, "visibilityJson cannot be null");

        this.modifiedDate = modifiedDate;
        this.modifiedBy = modifiedBy;
        this.confidence = confidence;
        this.derivedFromProperty = derivedFromProperty;
        this.visibilityJson = visibilityJson;
        this.propertyVisibility = propertyVisibility;
    }

    public PropertyMetadata(PropertyMetadata metadata) {
        this(
                metadata.getModifiedDate(),
                metadata.getModifiedBy(),
                metadata.getConfidence(),
                metadata.getDerivedFromProperty(),
                metadata.getVisibilityJson(),
                metadata.getPropertyVisibility()
        );
        for (AdditionalMetadataItem item : metadata.getAdditionalMetadataItems()) {
            add(item.getKey(), item.getValue(), item.getVisibility());
        }
    }

    public Metadata createMetadata() {
        Metadata metadata = new Metadata();
        VisalloProperties.MODIFIED_DATE_METADATA.setMetadata(metadata, modifiedDate, DEFAULT_VISIBILITY);
        VisalloProperties.MODIFIED_BY_METADATA.setMetadata(metadata, modifiedBy.getUserId(), DEFAULT_VISIBILITY);
        VisalloProperties.VISIBILITY_JSON_METADATA.setMetadata(metadata, visibilityJson, DEFAULT_VISIBILITY);
        if (confidence != null) {
            VisalloProperties.CONFIDENCE_METADATA.setMetadata(metadata, confidence, DEFAULT_VISIBILITY);
        }
        if (derivedFromProperty != null) {
            VisalloProperties.DERIVED_FROM_METADATA.setMetadata(metadata, derivedFromProperty, DEFAULT_VISIBILITY);
        }
        for (AdditionalMetadataItem additionalMetadataItem : additionalMetadataItems) {
            metadata.add(
                    additionalMetadataItem.getKey(),
                    additionalMetadataItem.getValue(),
                    additionalMetadataItem.getVisibility()
            );
        }
        return metadata;
    }

    public User getModifiedBy() {
        return modifiedBy;
    }

    public Date getModifiedDate() {
        return modifiedDate;
    }

    public Double getConfidence() {
        return confidence;
    }

    public DerivedFrom getDerivedFromProperty() {
        return derivedFromProperty;
    }

    public VisibilityJson getVisibilityJson() {
        return visibilityJson;
    }

    public Visibility getPropertyVisibility() {
        return propertyVisibility;
    }

    public Iterable<AdditionalMetadataItem> getAdditionalMetadataItems() {
        return additionalMetadataItems;
    }

    public void add(String key, Object value, Visibility visibility) {
        additionalMetadataItems.add(new AdditionalMetadataItem(key, value, visibility));
    }

    private static class AdditionalMetadataItem {
        private final String key;
        private final Object value;
        private final Visibility visibility;

        public AdditionalMetadataItem(String key, Object value, Visibility visibility) {
            this.key = key;
            this.value = value;
            this.visibility = visibility;
        }

        public String getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }

        public Visibility getVisibility() {
            return visibility;
        }
    }
}
