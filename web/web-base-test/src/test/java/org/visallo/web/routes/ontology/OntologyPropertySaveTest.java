package org.visallo.web.routes.ontology;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.visallo.core.exception.VisalloAccessDeniedException;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.ontology.*;
import org.visallo.core.model.workspace.WorkspaceUser;
import org.visallo.web.clientapi.model.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeSet;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OntologyPropertySaveTest extends OntologyRouteTestBase {
    private OntologyPropertySave route;

    @Before
    public void before() throws IOException {
        super.before();
        route = new OntologyPropertySave(ontologyRepository, workQueueRepository);
    }

    @Test
    public void testSaveNewProperty() throws Exception {
        when(privilegeRepository.hasPrivilege(user, Privilege.ONTOLOGY_ADD)).thenReturn(true);

        String propertyIRI = "junit-property";
        ClientApiOntology.Property response = route.handle(
                "New Property",
                "string",
                null,
                propertyIRI,
                new String[]{PUBLIC_CONCEPT_IRI},
                new String[]{PUBLIC_RELATIONSHIP_IRI},
                WORKSPACE_ID,
                user
        );

        // make sure the response looks ok
        assertEquals(propertyIRI, response.getTitle());
        assertEquals("New Property", response.getDisplayName());
        assertEquals(PropertyType.STRING, response.getDataType());
        assertEquals(new TreeSet<>(Arrays.asList("EXACT_MATCH", "FULL_TEXT")), new TreeSet<>(response.getTextIndexHints()));
        assertEquals(SandboxStatus.PRIVATE, response.getSandboxStatus());

        // make sure it's sandboxed in the ontology now
        OntologyProperty property = ontologyRepository.getPropertyByIRI(propertyIRI, WORKSPACE_ID);
        assertNotNull(property);
        assertEquals("New Property", property.getDisplayName());
        assertEquals(SandboxStatus.PRIVATE, property.getSandboxStatus());

        // make sure it was properly added to the concept
        Concept publicConcept = ontologyRepository.getConceptByIRI(PUBLIC_CONCEPT_IRI, WORKSPACE_ID);
        assertTrue(publicConcept.getProperties().stream().anyMatch(p -> p.getIri().equals(propertyIRI)));

        // make sure it was properly added to the relationship
        Relationship publicRelationship = ontologyRepository.getRelationshipByIRI(PUBLIC_RELATIONSHIP_IRI, WORKSPACE_ID);
        assertTrue(publicRelationship.getProperties().stream().anyMatch(p -> p.getIri().equals(propertyIRI)));

        // ensure it's not public
        assertNull(ontologyRepository.getPropertyByIRI(propertyIRI, OntologyRepository.PUBLIC));

        // Make sure we let the front end know
        Mockito.verify(workQueueRepository, Mockito.times(1)).pushOntologyChange(
                WORKSPACE_ID,
                Collections.singletonList(ontologyRepository.getConceptByIRI(PUBLIC_CONCEPT_IRI, WORKSPACE_ID).getId()),
                Collections.singletonList(ontologyRepository.getRelationshipByIRI(PUBLIC_RELATIONSHIP_IRI, WORKSPACE_ID).getId()),
                Collections.singletonList(property.getId()));
    }

    @Test
    public void testSaveNewPropertyWithDisplayType() throws Exception {
        when(privilegeRepository.hasPrivilege(user, Privilege.ONTOLOGY_ADD)).thenReturn(true);

        String propertyIRI = "junit-property";
        ClientApiOntology.Property response = route.handle(
                "New Property",
                "integer",
                "bytes",
                propertyIRI,
                new String[]{PUBLIC_CONCEPT_IRI},
                new String[]{PUBLIC_RELATIONSHIP_IRI},
                WORKSPACE_ID,
                user
        );

        // make sure the response looks ok
        assertEquals(propertyIRI, response.getTitle());
        assertEquals("New Property", response.getDisplayName());
        assertEquals(PropertyType.INTEGER, response.getDataType());
        assertEquals("bytes", response.getDisplayType());
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testSaveNewPropertyWithNoPrivilege() throws Exception {
        route.handle(
                "New Property",
                "string",
                null,
                "junit-property",
                new String[]{PUBLIC_CONCEPT_IRI},
                new String[]{PUBLIC_RELATIONSHIP_IRI},
                WORKSPACE_ID,
                user
        );
    }

    @Test
    public void testSaveNewPropertyWithUnknownConcept() throws Exception {
        try {
            route.handle(
                    "New Property",
                    "string",
                    null,
                    "junit-property",
                    new String[]{"unknown-concept"},
                    new String[]{PUBLIC_RELATIONSHIP_IRI},
                    WORKSPACE_ID,
                    user
            );
            fail("Expected to raise a VisalloException for unknown concept iri.");
        } catch (VisalloException ve) {
            assertEquals("Unable to load concept with IRI: unknown-concept", ve.getMessage());
        }
    }

    @Test
    public void testSaveNewPropertyWithUnknownRelationship() throws Exception {
        try {
            route.handle(
                    "New Property",
                    "string",
                    null,
                    "junit-property",
                    new String[]{PUBLIC_CONCEPT_IRI},
                    new String[]{"unknown-relationship"},
                    WORKSPACE_ID,
                    user
            );
            fail("Expected to raise a VisalloException for unknown relationship iri.");
        } catch (VisalloException ve) {
            assertEquals("Unable to load relationship with IRI: unknown-relationship", ve.getMessage());
        }
    }

    @Test
    public void testSaveNewPropertyWithUnknownPropertyType() throws Exception {
        try {
            route.handle(
                    "New Property",
                    "unknown-type",
                    null,
                    "junit-property",
                    new String[]{PUBLIC_CONCEPT_IRI},
                    new String[]{PUBLIC_RELATIONSHIP_IRI},
                    WORKSPACE_ID,
                    user
            );
            fail("Expected to raise a VisalloException for unknown property type.");
        } catch (VisalloException ve) {
            assertEquals("Unknown property type: unknown-type", ve.getMessage());
        }
    }

    @Test
    public void testAddAdditionalConceptAndRelationshipToNewProperty() throws Exception {
        when(privilegeRepository.hasPrivilege(user, Privilege.ONTOLOGY_ADD)).thenReturn(true);

        String propertyIRI = "junit-property";
        String displayName = "New Property";
        String dataType = "string";
        route.handle(displayName, dataType, null, propertyIRI, new String[]{PUBLIC_CONCEPT_IRI}, new String[]{PUBLIC_RELATIONSHIP_IRI}, WORKSPACE_ID, user);

        Concept publicConcept = ontologyRepository.getConceptByIRI(PUBLIC_CONCEPT_IRI, WORKSPACE_ID);
        assertTrue(publicConcept.getProperties().stream().anyMatch(p -> p.getIri().equals(propertyIRI)));

        Relationship publicRelationship = ontologyRepository.getRelationshipByIRI(PUBLIC_RELATIONSHIP_IRI, WORKSPACE_ID);
        assertTrue(publicRelationship.getProperties().stream().anyMatch(p -> p.getIri().equals(propertyIRI)));

        route.handle(displayName, dataType, null, propertyIRI, new String[]{PUBLIC_CONCEPT_IRI_B}, new String[]{PUBLIC_RELATIONSHIP_IRI_B}, WORKSPACE_ID, user);

        publicConcept = ontologyRepository.getConceptByIRI(PUBLIC_CONCEPT_IRI, WORKSPACE_ID);
        assertTrue(publicConcept.getProperties().stream().anyMatch(p -> p.getIri().equals(propertyIRI)));
        publicConcept = ontologyRepository.getConceptByIRI(PUBLIC_CONCEPT_IRI_B, WORKSPACE_ID);
        assertTrue(publicConcept.getProperties().stream().anyMatch(p -> p.getIri().equals(propertyIRI)));

        publicRelationship = ontologyRepository.getRelationshipByIRI(PUBLIC_RELATIONSHIP_IRI, WORKSPACE_ID);
        assertTrue(publicRelationship.getProperties().stream().anyMatch(p -> p.getIri().equals(propertyIRI)));
        publicRelationship = ontologyRepository.getRelationshipByIRI(PUBLIC_RELATIONSHIP_IRI_B, WORKSPACE_ID);
        assertTrue(publicRelationship.getProperties().stream().anyMatch(p -> p.getIri().equals(propertyIRI)));
    }

    @Test
    public void testSaveNewPropertyWithGeneratedIri() throws Exception {
        when(privilegeRepository.hasPrivilege(user, Privilege.ONTOLOGY_ADD)).thenReturn(true);

        String displayName = "New Property";
        String dataType = "string";
        String[] things = new String[]{ontologyRepository.getEntityConcept(OntologyRepository.PUBLIC).getIRI()};
        String[] relationships = new String[]{PUBLIC_RELATIONSHIP_IRI};
        ClientApiOntology.Property response = route.handle(displayName, dataType, null, null, things, relationships, WORKSPACE_ID, user);

        String originalIri = response.getTitle();
        assertTrue(originalIri.matches(OntologyRepositoryBase.BASE_OWL_IRI + "/new_property#[a-z0-9]+"));

        // ensure changing display name changes the iri
        response = route.handle(displayName + "1", dataType, null, null, things, relationships, WORKSPACE_ID, user);
        assertNotEquals(originalIri, response.getTitle());
        assertTrue(response.getTitle().matches(OntologyRepositoryBase.BASE_OWL_IRI + "/new_property1#[a-z0-9]+"));

        // ensure changing data type changes the iri
        response = route.handle(displayName, "integer", null, null, things, relationships, WORKSPACE_ID, user);
        assertNotEquals(originalIri, response.getTitle());
        assertTrue(response.getTitle().matches(OntologyRepositoryBase.BASE_OWL_IRI + "/new_property#[a-z0-9]+"));

        // ensure changing concepts does not change the iri
        response = route.handle(displayName, dataType, null, null, new String[]{PUBLIC_CONCEPT_IRI}, relationships, WORKSPACE_ID, user);
        assertEquals(originalIri, response.getTitle());

        // ensure changing relationships does not change the iri
        response = route.handle(displayName, dataType, null, null, things, new String[]{PUBLIC_RELATIONSHIP_IRI_B}, WORKSPACE_ID, user);
        assertEquals(originalIri, response.getTitle());

        // ensure changing workspace changes the iri
        WorkspaceUser workspaceUser = new WorkspaceUser(user.getUserId(), WorkspaceAccess.WRITE, true);
        when(workspaceRepository.findUsersWithAccess("other-workspace", user)).thenReturn(Collections.singletonList(workspaceUser));
        response = route.handle(displayName, dataType, null, null, things, relationships, "other-workspace", user);
        assertNotEquals(originalIri, response.getTitle());
        assertTrue(response.getTitle().matches(OntologyRepositoryBase.BASE_OWL_IRI + "/new_property#[a-z0-9]+"));
    }
}
