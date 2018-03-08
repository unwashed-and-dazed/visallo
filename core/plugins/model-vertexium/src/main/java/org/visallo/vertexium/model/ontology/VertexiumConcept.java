package org.visallo.vertexium.model.ontology;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.vertexium.Authorizations;
import org.vertexium.Metadata;
import org.vertexium.Vertex;
import org.vertexium.Visibility;
import org.vertexium.mutation.ElementMutation;
import org.vertexium.property.StreamingPropertyValue;
import org.vertexium.util.IterableUtils;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.ontology.Concept;
import org.visallo.core.model.ontology.OntologyProperties;
import org.visallo.core.model.ontology.OntologyProperty;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.user.User;
import org.visallo.core.util.JSONUtil;
import org.visallo.core.util.SandboxStatusUtil;
import org.visallo.web.clientapi.model.SandboxStatus;

import java.io.IOException;
import java.util.*;

public class VertexiumConcept extends Concept {
    private final Vertex vertex;
    private final String workspaceId;

    public VertexiumConcept(Vertex vertex, String workspaceId) {
        this(vertex, null, null, workspaceId);
    }

    public VertexiumConcept(Vertex vertex, String parentConceptIRI, Collection<OntologyProperty> properties, String workspaceId) {
        super(parentConceptIRI, properties);
        this.vertex = vertex;
        this.workspaceId = workspaceId;
    }

    @Override
    public String getIRI() {
        return OntologyProperties.ONTOLOGY_TITLE.getPropertyValue(vertex);
    }

    @Override
    public String getId() {
        return this.vertex.getId();
    }

    @Override
    public String getTitle() {
        return OntologyProperties.ONTOLOGY_TITLE.getPropertyValue(vertex);
    }

    @Override
    public boolean hasGlyphIconResource() {
        // TODO: This can be changed to GLYPH_ICON.getPropertyValue(vertex) once ENTITY_IMAGE_URL is added
        return vertex.getPropertyValue(OntologyProperties.GLYPH_ICON.getPropertyName()) != null ||
                vertex.getPropertyValue(OntologyProperties.GLYPH_ICON_FILE_NAME.getPropertyName()) != null;
    }

    @Override
    public boolean hasGlyphIconSelectedResource() {
        return vertex.getPropertyValue(OntologyProperties.GLYPH_ICON_SELECTED.getPropertyName()) != null ||
                vertex.getPropertyValue(OntologyProperties.GLYPH_ICON_SELECTED_FILE_NAME.getPropertyName()) != null;
    }

    @Override
    public String getColor() {
        return OntologyProperties.COLOR.getPropertyValue(vertex);
    }

    @Override
    public String getDisplayName() {
        return OntologyProperties.DISPLAY_NAME.getPropertyValue(vertex);
    }

    @Override
    public String getDisplayType() {
        return OntologyProperties.DISPLAY_TYPE.getPropertyValue(vertex);
    }

    @Override
    public String getTitleFormula() {
        return OntologyProperties.TITLE_FORMULA.getPropertyValue(vertex);
    }

    @Override
    public Boolean getSearchable() {
        return OntologyProperties.SEARCHABLE.getPropertyValue(vertex);
    }

    @Override
    public String getSubtitleFormula() {
        return OntologyProperties.SUBTITLE_FORMULA.getPropertyValue(vertex);
    }

    @Override
    public String getTimeFormula() {
        return OntologyProperties.TIME_FORMULA.getPropertyValue(vertex);
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
    public String[] getIntents() {
        return IterableUtils.toArray(OntologyProperties.INTENT.getPropertyValues(vertex), String.class);
    }

    @Override
    public void addIntent(String intent, User user, Authorizations authorizations) {
        Visibility visibility = OntologyRepository.VISIBILITY.getVisibility();
        Metadata metadata = createPropertyMetadata(user, new Date(), visibility);
        OntologyProperties.INTENT.addPropertyValue(vertex, intent, intent, metadata, visibility, authorizations);
        vertex.getGraph().flush();
    }

    @Override
    public void removeIntent(String intent, Authorizations authorizations) {
        OntologyProperties.INTENT.removeProperty(vertex, intent, authorizations);
        vertex.getGraph().flush();
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

    @Override
    public List<String> getAddRelatedConceptWhiteList() {
        JSONArray arr = OntologyProperties.ADD_RELATED_CONCEPT_WHITE_LIST.getPropertyValue(vertex);
        if (arr == null) {
            return null;
        }
        return JSONUtil.toStringList(arr);
    }

    @Override
    public void setProperty(String name, Object value, User user, Authorizations authorizations) {
        Visibility visibility = OntologyRepository.VISIBILITY.getVisibility();
        Metadata metadata = createPropertyMetadata(user, new Date(), visibility);
        getVertex().setProperty(name, value, metadata, OntologyRepository.VISIBILITY.getVisibility(), authorizations);
        getVertex().getGraph().flush();
    }

    public void removeProperty(String key, String name, Authorizations authorizations) {
        getVertex().softDeleteProperty(key, name, authorizations);
        getVertex().getGraph().flush();
    }

    @Override
    public void removeProperty(String name, Authorizations authorizations) {
        removeProperty(ElementMutation.DEFAULT_KEY, name, authorizations);
        getVertex().getGraph().flush();
    }

    @Override
    public byte[] getGlyphIcon() {
        try {
            StreamingPropertyValue spv = OntologyProperties.GLYPH_ICON.getPropertyValue(getVertex());
            if (spv == null) {
                return null;
            }
            return IOUtils.toByteArray(spv.getInputStream());
        } catch (IOException e) {
            throw new VisalloResourceNotFoundException("Could not retrieve glyph icon");
        }
    }

    @Override
    public byte[] getGlyphIconSelected() {
        try {
            StreamingPropertyValue spv = OntologyProperties.GLYPH_ICON_SELECTED.getPropertyValue(getVertex());
            if (spv == null) {
                return null;
            }
            return IOUtils.toByteArray(spv.getInputStream());
        } catch (IOException e) {
            throw new VisalloResourceNotFoundException("Could not retrieve glyph icon selected");
        }
    }

    @Override
    public String getGlyphIconFilePath() {
        return OntologyProperties.GLYPH_ICON_FILE_NAME.getPropertyValue(getVertex());
    }


    @Override
    public String getGlyphIconSelectedFilePath() {
        return OntologyProperties.GLYPH_ICON_SELECTED_FILE_NAME.getPropertyValue(getVertex());
    }

    @Override
    public byte[] getMapGlyphIcon() {
        try {
            StreamingPropertyValue spv = OntologyProperties.MAP_GLYPH_ICON.getPropertyValue(getVertex());
            if (spv == null) {
                return null;
            }
            return IOUtils.toByteArray(spv.getInputStream());
        } catch (IOException e) {
            throw new VisalloResourceNotFoundException("Could not retrieve map glyph icon");
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + (this.vertex != null ? this.vertex.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final VertexiumConcept other = (VertexiumConcept) obj;
        if (this.vertex != other.vertex && (this.vertex == null || !this.vertex.equals(other.vertex))) {
            return false;
        }
        return true;
    }

    public Vertex getVertex() {
        return this.vertex;
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
