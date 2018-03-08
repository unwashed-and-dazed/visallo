package org.visallo.vertexium.model.ontology;

import org.vertexium.Authorizations;
import org.vertexium.Metadata;
import org.vertexium.Vertex;
import org.vertexium.Visibility;
import org.vertexium.mutation.ElementMutation;
import org.vertexium.util.IterableUtils;
import org.visallo.core.model.ontology.OntologyProperties;
import org.visallo.core.model.ontology.OntologyProperty;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.model.ontology.Relationship;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.user.User;
import org.visallo.core.util.SandboxStatusUtil;
import org.visallo.web.clientapi.model.SandboxStatus;

import java.util.*;

public class VertexiumRelationship extends Relationship {
    private final Vertex vertex;
    private final List<String> inverseOfIRIs;
    private final String workspaceId;

    public VertexiumRelationship(
            String parentIRI,
            Vertex vertex,
            List<String> domainConceptIRIs,
            List<String> rangeConceptIRIs,
            List<String> inverseOfIRIs,
            Collection<OntologyProperty> properties,
            String workspaceId
    ) {
        super(parentIRI, domainConceptIRIs, rangeConceptIRIs, properties);
        this.vertex = vertex;
        this.inverseOfIRIs = inverseOfIRIs;
        this.workspaceId = workspaceId;
    }

    @Override
    public String getId() {
        return vertex.getId();
    }

    @Override
    public String[] getIntents() {
        return IterableUtils.toArray(OntologyProperties.INTENT.getPropertyValues(vertex), String.class);
    }

    @Override
    public void addIntent(String intent, User user, Authorizations authorizations) {
        Visibility visibility = OntologyRepository.VISIBILITY.getVisibility();
        Metadata metadata = createPropertyMetadata(user, new Date(), visibility);
        OntologyProperties.INTENT.addPropertyValue(vertex, intent, intent, metadata, visibility, authorizations);
        getVertex().getGraph().flush();
    }

    @Override
    public void removeIntent(String intent, Authorizations authorizations) {
        OntologyProperties.INTENT.removeProperty(vertex, intent, authorizations);
        getVertex().getGraph().flush();
    }

    @Override
    public void setProperty(String name, Object value, User user, Authorizations authorizations) {
        Visibility visibility = OntologyRepository.VISIBILITY.getVisibility();
        Metadata metadata = createPropertyMetadata(user, new Date(), visibility);
        getVertex().setProperty(name, value, metadata, visibility, authorizations);
        getVertex().getGraph().flush();
    }

    @Override
    public void removeProperty(String name, Authorizations authorizations) {
        getVertex().softDeleteProperty(ElementMutation.DEFAULT_KEY, name, authorizations);
        getVertex().getGraph().flush();
    }

    @Override
    public String getTitleFormula() {
        return OntologyProperties.TITLE_FORMULA.getPropertyValue(vertex);
    }

    @Override
    public String getSubtitleFormula() {
        return OntologyProperties.SUBTITLE_FORMULA.getPropertyValue(vertex);
    }

    @Override
    public String getTimeFormula() {
        return OntologyProperties.TIME_FORMULA.getPropertyValue(vertex);
    }

    public String getIRI() {
        return OntologyProperties.ONTOLOGY_TITLE.getPropertyValue(vertex);
    }

    public String getDisplayName() {
        return OntologyProperties.DISPLAY_NAME.getPropertyValue(vertex);
    }

    @Override
    public Iterable<String> getInverseOfIRIs() {
        return inverseOfIRIs;
    }

    @Override
    public boolean getUserVisible() {
        return OntologyProperties.USER_VISIBLE.getPropertyValue(vertex, true);
    }

    @Override
    public boolean getDeleteable() {
        return OntologyProperties.DELETEABLE.getPropertyValue(vertex, true);
    }

    @Override
    public boolean getUpdateable() {
        return OntologyProperties.UPDATEABLE.getPropertyValue(vertex, true);
    }

    @Override
    public Map<String, String> getMetadata() {
        Map<String, String> metadata = new HashMap<>();
        if (getSandboxStatus() == SandboxStatus.PRIVATE) {
            if (VisalloProperties.MODIFIED_BY.hasProperty(vertex)) {
                metadata.put(VisalloProperties.MODIFIED_BY.getPropertyName(), VisalloProperties.MODIFIED_BY.getPropertyValue(vertex));
            }
            if (VisalloProperties.MODIFIED_DATE.hasProperty(vertex)) {
                metadata.put(VisalloProperties.MODIFIED_DATE.getPropertyName(), VisalloProperties.MODIFIED_DATE.getPropertyValue(vertex).toString());
            }
        }
        return metadata;
    }

    public Vertex getVertex() {
        return vertex;
    }

    @Override
    public SandboxStatus getSandboxStatus() {
        return SandboxStatusUtil.getSandboxStatus(this.vertex, this.workspaceId);
    }

    private Metadata createPropertyMetadata(User user, Date modifiedDate, Visibility visibility) {
        Metadata metadata = new Metadata();
        VisalloProperties.MODIFIED_DATE_METADATA.setMetadata(metadata, modifiedDate, visibility);
        if (user != null) {
            VisalloProperties.MODIFIED_BY_METADATA.setMetadata(metadata, user.getUserId(), visibility);
        }
        return metadata;
    }
}
