package org.visallo.vertexium.model.ontology;

import org.vertexium.Authorizations;
import org.vertexium.util.ConvertingIterable;
import org.visallo.core.model.ontology.OntologyProperties;
import org.visallo.core.model.ontology.OntologyProperty;
import org.visallo.core.model.ontology.Relationship;
import org.visallo.core.user.User;
import org.visallo.web.clientapi.model.SandboxStatus;

import java.util.*;

public class InMemoryRelationship extends Relationship {
    private String relationshipIRI;
    private String displayName;
    private List<Relationship> inverseOfs = new ArrayList<>();
    private Set<String> intents = new HashSet<>();
    private boolean userVisible = true;
    private boolean deleteable;
    private boolean updateable;
    private String titleFormula;
    private String subtitleFormula;
    private String timeFormula;
    private String workspaceId;
    private Map<String, String> metadata = new HashMap<>();

    protected InMemoryRelationship(
            String parentIRI,
            String relationshipIRI,
            List<String> domainConceptIRIs,
            List<String> rangeConceptIRIs,
            Collection<OntologyProperty> properties,
            String workspaceId
    ) {
        super(parentIRI, domainConceptIRIs, rangeConceptIRIs, properties);
        this.relationshipIRI = relationshipIRI;
        this.workspaceId = workspaceId;
    }

    InMemoryRelationship shallowCopy() {
        InMemoryRelationship other = new InMemoryRelationship(
                getParentIRI(),
                getIRI(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                workspaceId);
        other.getDomainConceptIRIs().addAll(getDomainConceptIRIs());
        other.getRangeConceptIRIs().addAll(getRangeConceptIRIs());
        other.getProperties().addAll(getProperties());

        other.displayName = displayName;
        other.inverseOfs.addAll(inverseOfs);
        other.intents.addAll(intents);
        other.userVisible = userVisible;
        other.deleteable = deleteable;
        other.updateable = updateable;
        other.titleFormula = titleFormula;
        other.subtitleFormula = subtitleFormula;
        other.timeFormula = timeFormula;

        return other;
    }

    @Override
    public String getId() {
        return relationshipIRI;
    }

    @Override
    public String getIRI() {
        return relationshipIRI;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Iterable<String> getInverseOfIRIs() {
        return new ConvertingIterable<Relationship, String>(inverseOfs) {
            @Override
            protected String convert(Relationship o) {
                return o.getIRI();
            }
        };
    }

    @Override
    public boolean getUserVisible() {
        return userVisible;
    }

    @Override
    public boolean getDeleteable() {
        return deleteable;
    }

    @Override
    public boolean getUpdateable() {
        return updateable;
    }

    public String getTitleFormula() {
        return titleFormula;
    }

    @Override
    public String getSubtitleFormula() {
        return this.subtitleFormula;
    }

    @Override
    public String getTimeFormula() {
        return this.timeFormula;
    }

    @Override
    public String[] getIntents() {
        return this.intents.toArray(new String[this.intents.size()]);
    }

    @Override
    public void addIntent(String intent, User user, Authorizations authorizations) {
        this.intents.add(intent);
    }

    @Override
    public void removeIntent(String intent, Authorizations authorizations) {
        this.intents.remove(intent);
    }

    @Override
    public void setProperty(String name, Object value, User user, Authorizations authorizations) {
        if (OntologyProperties.DISPLAY_NAME.getPropertyName().equals(name)) {
            this.displayName = (String) value;
        } else if (OntologyProperties.TITLE_FORMULA.getPropertyName().equals(name)) {
            this.titleFormula = (String) value;
        } else if (OntologyProperties.SUBTITLE_FORMULA.getPropertyName().equals(name)) {
            this.subtitleFormula = (String) value;
        } else if (OntologyProperties.TIME_FORMULA.getPropertyName().equals(name)) {
            this.timeFormula = (String) value;
        } else if (OntologyProperties.USER_VISIBLE.getPropertyName().equals(name)) {
            this.userVisible = (Boolean) value;
        } else if (OntologyProperties.DELETEABLE.getPropertyName().equals(name)) {
            this.deleteable = (Boolean) value;
        } else if (OntologyProperties.UPDATEABLE.getPropertyName().equals(name)) {
            this.updateable = (Boolean) value;
        } else if (value != null) {
            metadata.put(name, value.toString());
        }
    }

    @Override
    public void removeProperty(String name, Authorizations authorizations) {
        if (OntologyProperties.DISPLAY_NAME.getPropertyName().equals(name)) {
            this.displayName = null;
        } else if (OntologyProperties.TITLE_FORMULA.getPropertyName().equals(name)) {
            this.titleFormula = null;
        } else if (OntologyProperties.SUBTITLE_FORMULA.getPropertyName().equals(name)) {
            this.subtitleFormula = null;
        } else if (OntologyProperties.TIME_FORMULA.getPropertyName().equals(name)) {
            this.timeFormula = null;
        } else if (OntologyProperties.USER_VISIBLE.getPropertyName().equals(name)) {
            this.userVisible = false;
        } else if (OntologyProperties.DELETEABLE.getPropertyName().equals(name)) {
            this.deleteable = false;
        } else if (OntologyProperties.UPDATEABLE.getPropertyName().equals(name)) {
            this.updateable = false;
        } else if (OntologyProperties.INTENT.getPropertyName().equals(name)) {
            intents.clear();
        } else {
            metadata.remove(name);
        }
    }

    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void addInverseOf(Relationship inverseOfRelationship) {
        inverseOfs.add(inverseOfRelationship);
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void removeWorkspaceId() {
        workspaceId = null;
    }

    void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    @Override
    public SandboxStatus getSandboxStatus() {
        return workspaceId == null ? SandboxStatus.PUBLIC : SandboxStatus.PRIVATE;
    }
}
