package org.visallo.web.routes.ontology;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.visallo.core.exception.VisalloAccessDeniedException;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.ontology.Concept;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.model.ontology.OntologyRepositoryBase;
import org.visallo.core.model.ontology.Relationship;
import org.visallo.core.model.workspace.WorkspaceUser;
import org.visallo.web.clientapi.model.ClientApiOntology;
import org.visallo.web.clientapi.model.Privilege;
import org.visallo.web.clientapi.model.SandboxStatus;
import org.visallo.web.clientapi.model.WorkspaceAccess;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OntologyRelationshipSaveTest extends OntologyRouteTestBase {
    private static final String SANDBOX_RELATIONSHIP_IRI = "sandbox-relationship";

    private OntologyRelationshipSave route;

    @Before
    public void before() throws IOException {
        super.before();
        route = new OntologyRelationshipSave(ontologyRepository, workQueueRepository);
    }

    @Test
    public void testSaveNewRelationship() throws Exception {
        when(privilegeRepository.hasPrivilege(user, Privilege.ONTOLOGY_ADD)).thenReturn(true);

        String relationshipIRI = "junit-relationship";
        ClientApiOntology.Relationship response = route.handle(
                "New Relationship",
                new String[]{PUBLIC_CONCEPT_IRI},
                new String[]{ontologyRepository.getEntityConcept(OntologyRepository.PUBLIC).getIRI()},
                PUBLIC_RELATIONSHIP_IRI,
                relationshipIRI,
                WORKSPACE_ID,
                workspaceAuthorizations,
                user
        );

        // make sure the response looks ok
        assertEquals(relationshipIRI, response.getTitle());
        assertEquals(PUBLIC_RELATIONSHIP_IRI, response.getParentIri());
        assertEquals("New Relationship", response.getDisplayName());
        assertEquals(SandboxStatus.PRIVATE, response.getSandboxStatus());
        assertEquals(1, response.getDomainConceptIris().size());
        assertEquals(PUBLIC_CONCEPT_IRI, response.getDomainConceptIris().get(0));
        assertEquals(1, response.getRangeConceptIris().size());
        assertEquals(ontologyRepository.getEntityConcept(OntologyRepository.PUBLIC).getIRI(), response.getRangeConceptIris().get(0));

        // make sure it's sandboxed in the ontology now
        Relationship relationship = ontologyRepository.getRelationshipByIRI(relationshipIRI, WORKSPACE_ID);
        assertNotNull(relationship);
        assertEquals("New Relationship", relationship.getDisplayName());
        assertEquals(SandboxStatus.PRIVATE, relationship.getSandboxStatus());
        assertEquals(PUBLIC_RELATIONSHIP_IRI, ontologyRepository.getParentRelationship(relationship, WORKSPACE_ID).getIRI());

        // ensure it's not public
        assertNull(ontologyRepository.getRelationshipByIRI(relationshipIRI, OntologyRepository.PUBLIC));

        // Make sure we let the front end know
        Mockito.verify(workQueueRepository, Mockito.times(1)).pushOntologyRelationshipsChange(WORKSPACE_ID, relationship.getId());
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testSaveNewRelationshipNoPrivilege() throws Exception {
        route.handle(
                "New Relationship",
                new String[]{PUBLIC_CONCEPT_IRI},
                new String[]{ontologyRepository.getEntityConcept(OntologyRepository.PUBLIC).getIRI()},
                PUBLIC_RELATIONSHIP_IRI,
                "junit-relationship",
                WORKSPACE_ID,
                workspaceAuthorizations,
                user
        );
    }

    @Test
    public void testSaveNewRelationshipWithUnknownDomainConcept() throws Exception {
        try {
            route.handle(
                    "New Relationship",
                    new String[]{"unknown-concept"},
                    new String[]{PUBLIC_CONCEPT_IRI},
                    null,
                    "junit-relationship",
                    WORKSPACE_ID,
                    workspaceAuthorizations,
                    user
            );
            fail("Expected to raise a VisalloException for unknown concept iri.");
        } catch (VisalloException ve) {
            assertEquals("Unable to load concept with IRI: unknown-concept", ve.getMessage());
        }
    }

    @Test
    public void testSaveNewRelationshipWithUnknownRangeConcept() throws Exception {
        try {
            route.handle(
                    "New Relationship",
                    new String[]{PUBLIC_CONCEPT_IRI},
                    new String[]{"unknown-concept"},
                    null,
                    "junit-relationship",
                    WORKSPACE_ID,
                    workspaceAuthorizations,
                    user
            );
            fail("Expected to raise a VisalloException for unknown concept iri.");
        } catch (VisalloException ve) {
            assertEquals("Unable to load concept with IRI: unknown-concept", ve.getMessage());
        }
    }

    @Test
    public void testSaveNewRelationshipWithUnknownParentIri() throws Exception {
        try {
            route.handle(
                    "New Relationship",
                    new String[]{PUBLIC_CONCEPT_IRI},
                    new String[]{ontologyRepository.getEntityConcept(OntologyRepository.PUBLIC).getIRI()},
                    "unknown-parent-relationship",
                    "junit-relationship",
                    WORKSPACE_ID,
                    workspaceAuthorizations,
                    user
            );
            fail("Expected to raise a VisalloException for unknown relationship iri.");
        } catch (VisalloException ve) {
            assertEquals("Unable to load parent relationship with IRI: unknown-parent-relationship", ve.getMessage());
        }
    }

    @Test
    public void testSaveNewRelationshipWithGeneratedIriAndNoParent() throws Exception {
        when(privilegeRepository.hasPrivilege(user, Privilege.ONTOLOGY_ADD)).thenReturn(true);

        String displayName = "New Relationship";
        String[] sourceConcepts = {PUBLIC_CONCEPT_IRI};
        String[] targetConcepts = {PUBLIC_CONCEPT_IRI_B};
        ClientApiOntology.Relationship response = route.handle(displayName, sourceConcepts, targetConcepts, null, null, WORKSPACE_ID, workspaceAuthorizations, user);

        String originalIri = response.getTitle();
        assertTrue(originalIri.matches(OntologyRepositoryBase.BASE_OWL_IRI + "/new_relationship#[a-z0-9]+"));
    }

    @Test
    public void testAddAdditionalConceptsToNewRelationship() throws Exception {
        when(privilegeRepository.hasPrivilege(user, Privilege.ONTOLOGY_ADD)).thenReturn(true);

        Concept thing = ontologyRepository.getEntityConcept(OntologyRepository.PUBLIC);
        String displayName = "New Relationship";
        String[] sourceConcepts = {thing.getIRI()};
        String[] targetConcepts = {PUBLIC_CONCEPT_IRI};
        ClientApiOntology.Relationship response = route.handle(displayName, sourceConcepts, targetConcepts, null, SANDBOX_RELATIONSHIP_IRI, WORKSPACE_ID, workspaceAuthorizations, user);

        assertEquals(1, response.getDomainConceptIris().size());
        assertEquals(thing.getIRI(), response.getDomainConceptIris().get(0));
        assertEquals(1, response.getRangeConceptIris().size());
        assertEquals(PUBLIC_CONCEPT_IRI, response.getRangeConceptIris().get(0));

        Relationship relationship = ontologyRepository.getRelationshipByIRI(SANDBOX_RELATIONSHIP_IRI, WORKSPACE_ID);
        assertEquals(1, relationship.getDomainConceptIRIs().size());
        assertEquals(thing.getIRI(), relationship.getDomainConceptIRIs().get(0));
        assertEquals(1, relationship.getRangeConceptIRIs().size());
        assertEquals(PUBLIC_CONCEPT_IRI, relationship.getRangeConceptIRIs().get(0));

        String sanboxConceptIri = "sandbox-concept-iri";
        ontologyRepository.getOrCreateConcept(thing, sanboxConceptIri, "Sandbox Concept", null, user, WORKSPACE_ID);
        ontologyRepository.clearCache();

        sourceConcepts = new String[]{PUBLIC_CONCEPT_IRI_B};
        targetConcepts = new String[]{sanboxConceptIri};
        response = route.handle(displayName, sourceConcepts, targetConcepts, null, SANDBOX_RELATIONSHIP_IRI, WORKSPACE_ID, workspaceAuthorizations, user);

        assertEquals(2, response.getDomainConceptIris().size());
        assertTrue(response.getDomainConceptIris().contains(thing.getIRI()));
        assertTrue(response.getDomainConceptIris().contains(PUBLIC_CONCEPT_IRI_B));
        assertEquals(2, response.getRangeConceptIris().size());
        assertTrue(response.getRangeConceptIris().contains(PUBLIC_CONCEPT_IRI));
        assertTrue(response.getRangeConceptIris().contains(sanboxConceptIri));

        relationship = ontologyRepository.getRelationshipByIRI(SANDBOX_RELATIONSHIP_IRI, WORKSPACE_ID);
        assertEquals(2, relationship.getDomainConceptIRIs().size());
        assertTrue(relationship.getDomainConceptIRIs().contains(thing.getIRI()));
        assertTrue(relationship.getDomainConceptIRIs().contains(PUBLIC_CONCEPT_IRI_B));
        assertEquals(2, relationship.getRangeConceptIRIs().size());
        assertTrue(relationship.getRangeConceptIRIs().contains(PUBLIC_CONCEPT_IRI));
        assertTrue(relationship.getRangeConceptIRIs().contains(sanboxConceptIri));
    }

    @Test
    public void testSaveNewRelationshipWithGeneratedIri() throws Exception {
        when(privilegeRepository.hasPrivilege(user, Privilege.ONTOLOGY_ADD)).thenReturn(true);

        String displayName = "New Relationship";
        String[] sourceConcepts = {PUBLIC_CONCEPT_IRI};
        String[] targetConcepts = {PUBLIC_CONCEPT_IRI_B};
        ClientApiOntology.Relationship response = route.handle(displayName, sourceConcepts, targetConcepts, PUBLIC_RELATIONSHIP_IRI, null, WORKSPACE_ID, workspaceAuthorizations, user);

        String originalIri = response.getTitle();
        assertTrue(originalIri.matches(OntologyRepositoryBase.BASE_OWL_IRI + "/new_relationship#[a-z0-9]+"));

        // ensure changing the display name changes the iri
        response = route.handle(displayName + "1", sourceConcepts, targetConcepts, PUBLIC_RELATIONSHIP_IRI, null, WORKSPACE_ID, workspaceAuthorizations, user);
        assertNotEquals(originalIri, response.getTitle());
        assertTrue(response.getTitle().matches(OntologyRepositoryBase.BASE_OWL_IRI + "/new_relationship1#[a-z0-9]+"));

        // ensure changing the source concepts does not change the iri
        response = route.handle(displayName, targetConcepts, targetConcepts, PUBLIC_RELATIONSHIP_IRI, null, WORKSPACE_ID, workspaceAuthorizations, user);
        assertEquals(originalIri, response.getTitle());

        // ensure changing the target concepts does not change the iri
        response = route.handle(displayName, sourceConcepts, sourceConcepts, PUBLIC_RELATIONSHIP_IRI, null, WORKSPACE_ID, workspaceAuthorizations, user);
        assertEquals(originalIri, response.getTitle());

        // ensure changing the parent iri changes the iri
        response = route.handle(displayName, sourceConcepts, targetConcepts, PUBLIC_RELATIONSHIP_IRI_B, null, WORKSPACE_ID, workspaceAuthorizations, user);
        assertNotEquals(originalIri, response.getTitle());
        assertTrue(response.getTitle().matches(OntologyRepositoryBase.BASE_OWL_IRI + "/new_relationship#[a-z0-9]+"));

        // ensure changing the workspace id changes the iri
        WorkspaceUser workspaceUser = new WorkspaceUser(user.getUserId(), WorkspaceAccess.WRITE, true);
        when(workspaceRepository.findUsersWithAccess("other-workspace", user)).thenReturn(Collections.singletonList(workspaceUser));
        response = route.handle(displayName, sourceConcepts, targetConcepts, PUBLIC_RELATIONSHIP_IRI, null, "other-workspace", workspaceAuthorizations, user);
        assertNotEquals(originalIri, response.getTitle());
        assertTrue(response.getTitle().matches(OntologyRepositoryBase.BASE_OWL_IRI + "/new_relationship#[a-z0-9]+"));
    }
}
