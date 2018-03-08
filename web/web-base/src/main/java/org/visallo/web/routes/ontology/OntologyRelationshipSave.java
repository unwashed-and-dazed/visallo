package org.visallo.web.routes.ontology;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Optional;
import org.visallo.webster.annotations.Required;
import org.vertexium.Authorizations;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.ontology.Concept;
import org.visallo.core.model.ontology.OntologyProperties;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.model.ontology.Relationship;
import org.visallo.core.model.workQueue.WorkQueueRepository;
import org.visallo.core.user.User;
import org.visallo.web.clientapi.model.ClientApiOntology;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class OntologyRelationshipSave extends OntologyBase {
    private final OntologyRepository ontologyRepository;
    private final WorkQueueRepository workQueueRepository;

    @Inject
    public OntologyRelationshipSave(
            final OntologyRepository ontologyRepository,
            final WorkQueueRepository workQueueRepository
    ) {
        super(ontologyRepository);
        this.ontologyRepository = ontologyRepository;
        this.workQueueRepository = workQueueRepository;
    }

    @Handle
    public ClientApiOntology.Relationship handle(
            @Required(name = "displayName", allowEmpty = false) String displayName,
            @Required(name = "sourceIris[]", allowEmpty = false) String[] sourceIris,
            @Required(name = "targetIris[]", allowEmpty = false) String[] targetIris,
            @Optional(name = "parentIri", allowEmpty = false) String parentIri,
            @Optional(name = "iri", allowEmpty = false) String relationshipIri,
            @ActiveWorkspaceId String workspaceId,
            Authorizations authorizations,
            User user) {


        List<Concept> domainConcepts = ontologyIrisToConcepts(sourceIris, workspaceId);
        List<Concept> rangeConcepts = ontologyIrisToConcepts(targetIris, workspaceId);

        if (relationshipIri == null) {
            if (parentIri != null) {
                relationshipIri = ontologyRepository.generateDynamicIri(Relationship.class, displayName, workspaceId, parentIri);
            } else {
                relationshipIri = ontologyRepository.generateDynamicIri(Relationship.class, displayName, workspaceId);
            }
        }

        Relationship parent = null;
        if (parentIri != null) {
            parent = ontologyRepository.getRelationshipByIRI(parentIri, workspaceId);
            if (parent == null) {
                throw new VisalloException("Unable to load parent relationship with IRI: " + parentIri);
            }
        }

        Relationship relationship = ontologyRepository.getRelationshipByIRI(relationshipIri, workspaceId);
        if (relationship == null) {
            relationship = ontologyRepository.getOrCreateRelationshipType(parent, domainConcepts, rangeConcepts, relationshipIri, displayName, false, user, workspaceId);
        } else {
            List<String> foundDomainIris = domainConcepts.stream().map(Concept::getIRI).collect(Collectors.toList());
            List<String> foundRangeIris = rangeConcepts.stream().map(Concept::getIRI).collect(Collectors.toList());
            ontologyRepository.addDomainConceptsToRelationshipType(relationshipIri, foundDomainIris, user, workspaceId);
            ontologyRepository.addRangeConceptsToRelationshipType(relationshipIri, foundRangeIris, user, workspaceId);
        }
        relationship.setProperty(OntologyProperties.DISPLAY_NAME.getPropertyName(), displayName, user, authorizations);

        ontologyRepository.clearCache(workspaceId);
        workQueueRepository.pushOntologyRelationshipsChange(workspaceId, relationship.getId());

        return ontologyRepository.getRelationshipByIRI(relationshipIri, workspaceId).toClientApi();
    }
}
