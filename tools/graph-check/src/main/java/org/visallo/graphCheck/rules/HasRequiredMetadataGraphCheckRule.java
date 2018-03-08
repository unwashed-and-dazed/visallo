package org.visallo.graphCheck.rules;

import org.vertexium.Element;
import org.vertexium.Property;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.graphCheck.DefaultGraphCheckRule;
import org.visallo.graphCheck.GraphCheckContext;

public class HasRequiredMetadataGraphCheckRule extends DefaultGraphCheckRule {
    @Override
    public void visitElement(GraphCheckContext ctx, Element element) {
        checkElementHasProperty(ctx, element, VisalloProperties.MODIFIED_BY.getPropertyName());
        checkElementHasProperty(ctx, element, VisalloProperties.MODIFIED_DATE.getPropertyName());
        checkElementHasProperty(ctx, element, VisalloProperties.VISIBILITY_JSON.getPropertyName());
    }

    @Override
    public void visitProperty(GraphCheckContext ctx, Element element, Property property) {
        if (!property.getName().equals(VisalloProperties.CONCEPT_TYPE.getPropertyName()) &&
                !property.getName().equals(VisalloProperties.MODIFIED_BY.getPropertyName()) &&
                !property.getName().equals(VisalloProperties.MODIFIED_DATE.getPropertyName()) &&
                !property.getName().equals(VisalloProperties.VISIBILITY_JSON.getPropertyName())) {
            checkPropertyHasMetadata(ctx, element, property, VisalloProperties.MODIFIED_BY_METADATA.getMetadataKey());
            checkPropertyHasMetadata(ctx, element, property, VisalloProperties.MODIFIED_DATE_METADATA.getMetadataKey());
        }
    }
}
