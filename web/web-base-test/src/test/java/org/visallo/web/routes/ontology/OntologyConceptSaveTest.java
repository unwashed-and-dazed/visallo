package org.visallo.web.routes.ontology;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.visallo.core.exception.VisalloAccessDeniedException;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.ontology.Concept;
import org.visallo.core.model.ontology.OntologyRepositoryBase;
import org.visallo.core.model.workspace.WorkspaceUser;
import org.visallo.web.clientapi.model.ClientApiOntology;
import org.visallo.web.clientapi.model.Privilege;
import org.visallo.web.clientapi.model.SandboxStatus;
import org.visallo.web.clientapi.model.WorkspaceAccess;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.visallo.core.model.ontology.OntologyRepository.PUBLIC;

@RunWith(MockitoJUnitRunner.class)
public class OntologyConceptSaveTest extends OntologyRouteTestBase {
    private OntologyConceptSave route;

    @Before
    public void before() throws IOException {
        super.before();
        route = new OntologyConceptSave(ontologyRepository, workQueueRepository);
    }

    @Test
    public void testSaveNewConcept() throws Exception {
        when(privilegeRepository.hasPrivilege(user, Privilege.ONTOLOGY_ADD)).thenReturn(true);

        String conceptIri = "new-concept-iri";
        String displayName = "New Concept";
        ClientApiOntology.Concept response = route.handle(
                displayName,
                conceptIri,
                ontologyRepository.getEntityConcept(PUBLIC).getIRI(),
                "glyph.png",
                "red",
                WORKSPACE_ID,
                user
        );

        assertEquals(conceptIri, response.getId());
        assertEquals(ontologyRepository.getEntityConcept(PUBLIC).getIRI(), response.getParentConcept());
        assertEquals(displayName, response.getDisplayName());
        assertEquals("resource?id=new-concept-iri", response.getGlyphIconHref());
        assertEquals("red", response.getColor());
        assertEquals(SandboxStatus.PRIVATE, response.getSandboxStatus());

        // make sure it's sandboxed in the ontology now
        Concept concept = ontologyRepository.getConceptByIRI(conceptIri, WORKSPACE_ID);
        assertNotNull(concept);
        assertEquals("New Concept", concept.getDisplayName());
        assertEquals(SandboxStatus.PRIVATE, concept.getSandboxStatus());
        assertEquals(ontologyRepository.getEntityConcept(PUBLIC).getIRI(), ontologyRepository.getParentConcept(concept, WORKSPACE_ID).getIRI());

        // ensure it's not public
        assertNull(ontologyRepository.getConceptByIRI(conceptIri, PUBLIC));

        // Make sure we let the front end know
        Mockito.verify(workQueueRepository, Mockito.times(1)).pushOntologyConceptsChange(WORKSPACE_ID, concept.getId());
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testSaveNewConceptNoPermission() throws Exception {
        route.handle(
                "New Concept",
                "new-concept-iri",
                ontologyRepository.getEntityConcept(PUBLIC).getIRI(),
                "glyph.png",
                "red",
                WORKSPACE_ID,
                user
        );
    }

    @Test
    public void testSaveNewConceptWithUnknownParentConcept() throws Exception {
        when(privilegeRepository.hasPrivilege(user, Privilege.ONTOLOGY_ADD)).thenReturn(true);

        try {
            route.handle(
                    "New Concept",
                    "new-concept-iri",
                    "unknown-parent-iri",
                    "glyph.png",
                    "red",
                    WORKSPACE_ID,
                    user
            );
            fail("Expected to get an exception for unknown parent concept");
        } catch (VisalloException ve) {
            assertEquals("Unable to find parent concept with IRI: unknown-parent-iri", ve.getMessage());
        }
    }

    @Test
    public void testSaveNewConceptWithGeneratedIriAndNoParent() throws Exception {
        when(privilegeRepository.hasPrivilege(user, Privilege.ONTOLOGY_ADD)).thenReturn(true);

        String displayName = "New Concept";
        String glyph = "glyph.png";
        String color = "red";
        ClientApiOntology.Concept response = route.handle(displayName, null, null, glyph, color, WORKSPACE_ID, user);

        String originalIri = response.getTitle();
        assertTrue(originalIri.matches(OntologyRepositoryBase.BASE_OWL_IRI + "/new_concept#[a-z0-9]+"));
    }

    @Test
    public void testSaveNewConceptWithGeneratedIri() throws Exception {
        when(privilegeRepository.hasPrivilege(user, Privilege.ONTOLOGY_ADD)).thenReturn(true);

        String thingIri = ontologyRepository.getEntityConcept(PUBLIC).getIRI();

        String displayName = "New Concept";
        String glyph = "glyph.png";
        String color = "red";
        ClientApiOntology.Concept response = route.handle(displayName, null, thingIri, glyph, color, WORKSPACE_ID, user);

        String originalIri = response.getTitle();
        assertTrue(originalIri.matches(OntologyRepositoryBase.BASE_OWL_IRI + "/new_concept#[a-z0-9]+"));

        // ensure that the same details get the same iri
        response = route.handle(displayName, null, thingIri, glyph, color, WORKSPACE_ID, user);
        assertEquals(originalIri, response.getTitle());

        // ensure that changing glyph and color don't change the iri
        response = route.handle(displayName, null, thingIri, "other.png", "orange", WORKSPACE_ID, user);
        assertEquals(originalIri, response.getTitle());

        // ensure that changing display name changes the iri
        response = route.handle(displayName + "1", null, thingIri, "other.png", "orange", WORKSPACE_ID, user);
        assertNotEquals(originalIri, response.getTitle());
        assertTrue(response.getTitle().matches(OntologyRepositoryBase.BASE_OWL_IRI + "/new_concept1#[a-z0-9]+"));

        // ensure that changing parent changes the iri
        response = route.handle(displayName, null, PUBLIC_CONCEPT_IRI, "other.png", "orange", WORKSPACE_ID, user);
        assertNotEquals(originalIri, response.getTitle());
        assertTrue(response.getTitle().matches(OntologyRepositoryBase.BASE_OWL_IRI + "/new_concept#[a-z0-9]+"));

        // ensure that changing workspace changes the iri
        WorkspaceUser workspaceUser = new WorkspaceUser(user.getUserId(), WorkspaceAccess.WRITE, true);
        when(workspaceRepository.findUsersWithAccess("other-workspace", user)).thenReturn(Collections.singletonList(workspaceUser));
        response = route.handle(displayName, null, thingIri, "other.png", "orange", "other-workspace", user);
        assertNotEquals(originalIri, response.getTitle());
        assertTrue(response.getTitle().matches(OntologyRepositoryBase.BASE_OWL_IRI + "/new_concept#[a-z0-9]+"));
    }
}