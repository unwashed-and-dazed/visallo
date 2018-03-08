package org.visallo.core.model.ontology;

import com.beust.jcommander.internal.Lists;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.semanticweb.owlapi.model.IRI;
import org.vertexium.*;
import org.vertexium.util.IterableUtils;
import org.visallo.core.exception.VisalloAccessDeniedException;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.workspace.Workspace;
import org.visallo.core.user.SystemUser;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloInMemoryTestBase;
import org.visallo.web.clientapi.model.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.Assert.*;
import static org.visallo.core.model.ontology.OntologyRepository.PUBLIC;

public abstract class OntologyRepositoryTestBase extends VisalloInMemoryTestBase {
    private static final String TEST_OWL = "test.owl";
    private static final String TEST_CHANGED_OWL = "test_changed.owl";
    private static final String TEST01_OWL = "test01.owl";
    private static final String GLYPH_ICON_FILE = "glyphicons_003_user@2x.png";
    private static final String TEST_IRI = "http://visallo.org/test";

    private static final String TEST_HIERARCHY_IRI = "http://visallo.org/testhierarchy";
    private static final String TEST_HIERARCHY_OWL = "test_hierarchy.owl";

    private static final String TEST01_IRI = "http://visallo.org/test01";

    private static final String SANDBOX_CONCEPT_IRI = "sandbox-concept-iri";
    private static final String SANDBOX_RELATIONSHIP_IRI = "sandbox-relationship-iri";
    private static final String SANDBOX_PROPERTY_IRI = "sandbox-property-iri";
    private static final String SANDBOX_PROPERTY_IRI_ONLY_SANDBOXED_CONCEPT = "sandbox-property-iri2";
    private static final String SANDBOX_DISPLAY_NAME = "Sandbox Display";
    private static final String PUBLIC_CONCEPT_IRI = "public-concept-iri";
    private static final String PUBLIC_RELATIONSHIP_IRI = "public-relationship-iri";
    private static final String PUBLIC_PROPERTY_IRI = "public-property-iri";
    private static final String PUBLIC_DISPLAY_NAME = "Public Display";

    private String workspaceId = "junit-workspace";
    private User systemUser = new SystemUser();

    private Authorizations authorizations;
    private User user;
    private User adminUser;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void before() throws Exception {
        super.before();
        authorizations = getGraph().createAuthorizations();
        user = getUserRepository().findOrAddUser("junit", "Junit", "junit@visallo.com", "password");
        Workspace workspace = getWorkspaceRepository().add(workspaceId, "Junit Workspace", user);
        if (getPrivilegeRepository().hasPrivilege(user, Privilege.ADMIN)) {
            fail("User shouldn't have admin");
        }

        adminUser = getUserRepository().findOrAddUser("junit-admin", "Junit Admin", "junit-admin@visallo.com", "password");
        Set<String> privileges = Privilege.ALL_BUILT_IN.stream().map(Privilege::getName).collect(Collectors.toSet());
        setPrivileges(adminUser, privileges);

        getWorkspaceRepository().updateUserOnWorkspace(workspace, adminUser.getUserId(), WorkspaceAccess.WRITE, systemUser);
    }

    @Test
    public void testChangingDisplayAnnotationsShouldSucceed() throws Exception {
        loadTestOwlFile();

        importTestOntologyFile(TEST_CHANGED_OWL, TEST_IRI);

        validateChangedOwlRelationships();
        validateChangedOwlConcepts();
        validateChangedOwlProperties();
    }

    @Test
    public void testGettingParentConceptReturnsParentProperties() throws Exception {
        loadHierarchyOwlFile();
        Concept concept = getOntologyRepository().getConceptByIRI(TEST_HIERARCHY_IRI + "#person", PUBLIC);
        Concept parentConcept = getOntologyRepository().getParentConcept(concept, PUBLIC);
        assertEquals(1, parentConcept.getProperties().size());
    }

    @Test
    public void testRelationshipHierarchy() throws Exception {
        loadHierarchyOwlFile();

        Relationship relationship = getOntologyRepository().getRelationshipByIRI(TEST_HIERARCHY_IRI + "#personReallyKnowsPerson", PUBLIC);
        assertEquals(TEST_HIERARCHY_IRI + "#personKnowsPerson", relationship.getParentIRI());

        relationship = getOntologyRepository().getParentRelationship(relationship, PUBLIC);
        assertEquals(TEST_HIERARCHY_IRI + "#personKnowsPerson", relationship.getIRI());
        assertEquals(OntologyRepositoryBase.TOP_OBJECT_PROPERTY_IRI, relationship.getParentIRI());
    }

    @Test
    public void testDependenciesBetweenOntologyFilesShouldNotChangeParentProperties() throws Exception {
        loadTestOwlFile();

        importTestOntologyFile(TEST01_OWL, TEST01_IRI);
        validateTestOwlRelationship();
        validateTestOwlConcepts(3);
        validateTestOwlProperties();

        OntologyProperty aliasProperty = getOntologyRepository().getPropertyByIRI(TEST01_IRI + "#alias", PUBLIC);
        Concept person = getOntologyRepository().getConceptByIRI(TEST_IRI + "#person", PUBLIC);
        assertTrue(person.getProperties()
                .stream()
                .anyMatch(p -> p.getIri().equals(aliasProperty.getIri()))
        );
    }

    @Test
    public void testGenerateIri() throws Exception {
        assertEquals("Should lowercase", "http://visallo.org/xxx#545f80459971026861f7d0a767a058474788f5d8", getOntologyRepository().generateDynamicIri(Concept.class, "XxX", "w0"));
        assertEquals("Extended data should change hash", "http://visallo.org/xxx#a3ee0f2fcbb97cd46570913b304940dfd563d0dd", getOntologyRepository().generateDynamicIri(Concept.class, "XxX", "w0", "1"));
        assertEquals("replace spaces", "http://visallo.org/s_1_2_3#b326c1112fdf23093cc7b2b964294a9afd2530ec", getOntologyRepository().generateDynamicIri(Concept.class, " S 1 2 3 ", "w0"));
        assertEquals("replace non-alpha-num", "http://visallo.org/a_a1#4db31fddc58acba07547c744ee9f1edae49ad22d", getOntologyRepository().generateDynamicIri(Concept.class, "a !@#A$%1^&*()<>?\":{}=+),[]\\|`~", "w0"));


        StringBuilder valid = new StringBuilder();
        StringBuilder invalid = new StringBuilder();
        for (int i = 0; i < OntologyRepositoryBase.MAX_DISPLAY_NAME + 2; i++) {
            if (i < OntologyRepositoryBase.MAX_DISPLAY_NAME) valid.append("a");
            invalid.append("a");
        }
        assertEquals("length check valid", "http://visallo.org/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa#2f36190a6f38c1c1d1bc7d8a5e1f00cd71e5dc74", getOntologyRepository().generateDynamicIri(Concept.class, valid.toString(), "w0"));
        assertEquals("length/hash check invalid", "http://visallo.org/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa#2f36190a6f38c1c1d1bc7d8a5e1f00cd71e5dc74", getOntologyRepository().generateDynamicIri(Concept.class, invalid.toString(), "w0"));

    }

    @Test
    public void testGetConceptsWithProperties() throws Exception {
        loadHierarchyOwlFile();
        getOntologyRepository().clearCache();

        Iterable<Concept> conceptsWithProperties = getOntologyRepository().getConceptsWithProperties(workspaceId);
        Map<String, Concept> conceptsByIri = StreamSupport.stream(conceptsWithProperties.spliterator(), false)
                .collect(Collectors.toMap(Concept::getIRI, Function.identity()));

        Concept personConcept = conceptsByIri.get("http://visallo.org/testhierarchy#person");

        // Check parent iris
        assertNull(conceptsByIri.get("http://visallo.org#root").getParentConceptIRI());
        assertEquals("http://visallo.org#root", conceptsByIri.get("http://www.w3.org/2002/07/owl#Thing").getParentConceptIRI());
        assertEquals("http://www.w3.org/2002/07/owl#Thing", conceptsByIri.get("http://visallo.org/testhierarchy#contact").getParentConceptIRI());
        assertEquals("http://visallo.org/testhierarchy#contact", personConcept.getParentConceptIRI());

        // Check properties
        List<OntologyProperty> personProperties = new ArrayList<>(personConcept.getProperties());
        assertEquals(1, personProperties.size());
        assertEquals("http://visallo.org/testhierarchy#name", personProperties.get(0).getIri());

        // Check intents
        List<String> intents = Arrays.asList(personConcept.getIntents());
        assertEquals(2, intents.size());
        assertTrue(intents.contains("face"));
        assertTrue(intents.contains("person"));

        // Spot check other concept values
        assertEquals("Person", personConcept.getDisplayName());
        assertEquals("prop('http://visallo.org/testhierarchy#name') || ''", personConcept.getTitleFormula());
    }

    @Test
    public void testGetRelationships() throws Exception {
        loadHierarchyOwlFile();
        getOntologyRepository().clearCache();

        Iterable<Relationship> relationships = getOntologyRepository().getRelationships(workspaceId);
        Map<String, Relationship> relationshipsByIri = StreamSupport.stream(relationships.spliterator(), false)
                .collect(Collectors.toMap(Relationship::getIRI, Function.identity()));

        assertNull(relationshipsByIri.get("http://www.w3.org/2002/07/owl#topObjectProperty").getParentIRI());
        assertEquals("http://www.w3.org/2002/07/owl#topObjectProperty", relationshipsByIri.get("http://visallo.org/testhierarchy#personKnowsPerson").getParentIRI());
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testExceptionWhenDeletingPublicConcepts() throws Exception {
        createSampleOntology();
        getOntologyRepository().deleteConcept(PUBLIC_CONCEPT_IRI, user, null);
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testExceptionWhenDeletingSandboxedConceptsAsNonAdmin() throws Exception {
        createSampleOntology();
        getOntologyRepository().deleteConcept(SANDBOX_CONCEPT_IRI, user, workspaceId);
    }

    @Test
    public void testExceptionDeletingSandboxedConceptsWithVertices() throws Exception {
        createSampleOntology();
        Concept concept = getOntologyRepository().getConceptByIRI(SANDBOX_CONCEPT_IRI, workspaceId);
        assertTrue("Concept exists", concept != null && concept.getIRI().equals(SANDBOX_CONCEPT_IRI));

        VisibilityJson json = VisibilityJson.updateVisibilitySourceAndAddWorkspaceId(new VisibilityJson(), "", workspaceId);
        Visibility visibility = getVisibilityTranslator().toVisibility(json).getVisibility();
        VertexBuilder vb = getGraph().prepareVertex(visibility);
        vb.setProperty(VisalloProperties.CONCEPT_TYPE.getPropertyName(), SANDBOX_CONCEPT_IRI, visibility);
        vb.save(authorizations);

        thrown.expect(VisalloException.class);
        thrown.expectMessage("Unable to delete concept that have vertices assigned to it");
        getOntologyRepository().deleteConcept(SANDBOX_CONCEPT_IRI, adminUser, workspaceId);
    }

    @Test
    public void testExceptionDeletingSandboxedConceptsWithDescendants() throws Exception {
        createSampleOntology();
        Concept concept = getOntologyRepository().getConceptByIRI(SANDBOX_CONCEPT_IRI, workspaceId);

        // Add a descendant
        getOntologyRepository().getOrCreateConcept(concept, SANDBOX_CONCEPT_IRI + "child", SANDBOX_DISPLAY_NAME, null, systemUser, workspaceId);
        getOntologyRepository().clearCache(workspaceId);

        thrown.expect(VisalloException.class);
        thrown.expectMessage("Unable to delete concept that have children");
        getOntologyRepository().deleteConcept(SANDBOX_CONCEPT_IRI, adminUser, workspaceId);
    }

    @Test
    public void testExceptionDeletingSandboxedConceptsWithRelationshipsDomain() throws Exception {
        createSampleOntology();
        Concept concept = getOntologyRepository().getConceptByIRI(SANDBOX_CONCEPT_IRI, workspaceId);

        // Add an edge type
        List<Concept> things = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        getOntologyRepository().getOrCreateRelationshipType(null, Arrays.asList(concept), things, "sandboxed-relationship-withsandboxed-concepts", true, adminUser, workspaceId);
        getOntologyRepository().clearCache(workspaceId);

        thrown.expect(VisalloException.class);
        thrown.expectMessage("Unable to delete concept that is used in domain/range of relationship");
        getOntologyRepository().deleteConcept(SANDBOX_CONCEPT_IRI, adminUser, workspaceId);
    }

    @Test
    public void testExceptionDeletingSandboxedConceptsWithRelationshipsRange() throws Exception {
        createSampleOntology();
        Concept concept = getOntologyRepository().getConceptByIRI(SANDBOX_CONCEPT_IRI, workspaceId);

        // Add an edge type
        List<Concept> things = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        getOntologyRepository().getOrCreateRelationshipType(null, things, Arrays.asList(concept), "sandboxed-relationship-withsandboxed-concepts", true, adminUser, workspaceId);
        getOntologyRepository().clearCache(workspaceId);

        thrown.expect(VisalloException.class);
        thrown.expectMessage("Unable to delete concept that is used in domain/range of relationship");
        getOntologyRepository().deleteConcept(SANDBOX_CONCEPT_IRI, adminUser, workspaceId);
    }

    @Test
    public void testDeletingSandboxedConcepts() throws Exception {
        createSampleOntology();
        Concept concept = getOntologyRepository().getConceptByIRI(SANDBOX_CONCEPT_IRI, workspaceId);
        assertTrue("Concept exists", concept != null && concept.getIRI().equals(SANDBOX_CONCEPT_IRI));

        OntologyProperty property = getOntologyRepository().getPropertyByIRI(SANDBOX_PROPERTY_IRI_ONLY_SANDBOXED_CONCEPT, workspaceId);
        assertTrue("Property exists", property != null && property.getIri().equals(SANDBOX_PROPERTY_IRI_ONLY_SANDBOXED_CONCEPT));
        assertTrue("Concept has property", concept.getProperties().stream().anyMatch(ontologyProperty -> ontologyProperty.getIri().equals(SANDBOX_PROPERTY_IRI_ONLY_SANDBOXED_CONCEPT)));

        getOntologyRepository().deleteConcept(SANDBOX_CONCEPT_IRI, adminUser, workspaceId);
        getOntologyRepository().clearCache(workspaceId);

        concept = getOntologyRepository().getConceptByIRI(SANDBOX_CONCEPT_IRI, workspaceId);
        assertTrue("Concept should have been deleted", concept == null);

        property = getOntologyRepository().getPropertyByIRI(SANDBOX_PROPERTY_IRI_ONLY_SANDBOXED_CONCEPT, workspaceId);
        assertTrue("Property only used in this concept is deleted", property == null);

        property = getOntologyRepository().getPropertyByIRI(SANDBOX_PROPERTY_IRI, workspaceId);
        assertTrue("Property used in other concepts is updated", property != null);
        assertEquals(1, property.getConceptIris().size());
        assertEquals(PUBLIC_CONCEPT_IRI, property.getConceptIris().get(0));
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testExceptionWhenDeletingPublicProperties() throws Exception {
        createSampleOntology();
        getOntologyRepository().deleteProperty(PUBLIC_PROPERTY_IRI, user, null);
    }

    @Test
    public void testExceptionDeletingSandboxedPropertiesWithVertices() throws Exception {
        createSampleOntology();

        VisibilityJson json = VisibilityJson.updateVisibilitySourceAndAddWorkspaceId(new VisibilityJson(), "", workspaceId);
        Visibility visibility = getVisibilityTranslator().toVisibility(json).getVisibility();
        VertexBuilder vb = getGraph().prepareVertex(visibility);
        vb.setProperty(SANDBOX_PROPERTY_IRI, "a value", visibility);
        vb.save(authorizations);

        thrown.expect(VisalloException.class);
        thrown.expectMessage("Unable to delete property that have elements using it");
        getOntologyRepository().deleteProperty(SANDBOX_PROPERTY_IRI, adminUser, workspaceId);
    }

    @Test
    public void testExceptionDeletingSandboxedPropertiesWithEdges() throws Exception {
        createSampleOntology();

        VisibilityJson json = VisibilityJson.updateVisibilitySourceAndAddWorkspaceId(new VisibilityJson(), "", workspaceId);
        Visibility visibility = getVisibilityTranslator().toVisibility(json).getVisibility();

        Vertex source = getGraph().prepareVertex(visibility).save(authorizations);
        Vertex target = getGraph().prepareVertex(visibility).save(authorizations);

        EdgeBuilder edgeBuilder = getGraph().prepareEdge(source, target, SANDBOX_RELATIONSHIP_IRI, visibility);
        edgeBuilder.setProperty(SANDBOX_PROPERTY_IRI, "a value", visibility);
        edgeBuilder.save(authorizations);

        thrown.expect(VisalloException.class);
        thrown.expectMessage("Unable to delete property that have elements using it");
        getOntologyRepository().deleteProperty(SANDBOX_PROPERTY_IRI, adminUser, workspaceId);
    }

    @Test
    public void testDeletingProperties() throws Exception {
        createSampleOntology();
        OntologyProperty property = getOntologyRepository().getPropertyByIRI(SANDBOX_PROPERTY_IRI, workspaceId);
        assertTrue("Property exists", property != null);
        getOntologyRepository().deleteProperty(SANDBOX_PROPERTY_IRI, adminUser, workspaceId);
        getOntologyRepository().clearCache(workspaceId);

        property = getOntologyRepository().getPropertyByIRI(SANDBOX_PROPERTY_IRI, workspaceId);
        assertTrue("Property is deleted", property == null);
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testExceptionWhenDeletingPublicRelationships() throws Exception {
        createSampleOntology();
        getOntologyRepository().deleteRelationship(PUBLIC_RELATIONSHIP_IRI, user, null);
    }

    @Test
    public void testExceptionDeletingSandboxedRelationshipsWithEdges() throws Exception {
        createSampleOntology();

        VisibilityJson json = VisibilityJson.updateVisibilitySourceAndAddWorkspaceId(new VisibilityJson(), "", workspaceId);
        Visibility visibility = getVisibilityTranslator().toVisibility(json).getVisibility();

        Vertex source = getGraph().prepareVertex(visibility).save(authorizations);
        Vertex target = getGraph().prepareVertex(visibility).save(authorizations);
        EdgeBuilder edgeBuilder = getGraph().prepareEdge(source, target, SANDBOX_RELATIONSHIP_IRI, visibility);
        edgeBuilder.setProperty(SANDBOX_PROPERTY_IRI, "a value", visibility);
        edgeBuilder.save(authorizations);

        thrown.expect(VisalloException.class);
        thrown.expectMessage("Unable to delete relationship that have edges using it");
        getOntologyRepository().deleteRelationship(SANDBOX_RELATIONSHIP_IRI, adminUser, workspaceId);
    }

    @Test
    public void testExceptionDeletingSandboxedRelationshipsWithDescendants() throws Exception {
        createSampleOntology();

        Relationship relationship = getOntologyRepository().getRelationshipByIRI(SANDBOX_RELATIONSHIP_IRI, workspaceId);

        VisibilityJson.updateVisibilitySourceAndAddWorkspaceId(new VisibilityJson(), "", workspaceId);

        List<Concept> things = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        getOntologyRepository().getOrCreateRelationshipType(relationship, things, things, SANDBOX_RELATIONSHIP_IRI + "child", true, adminUser, workspaceId);
        getOntologyRepository().clearCache(workspaceId);

        thrown.expect(VisalloException.class);
        thrown.expectMessage("Unable to delete relationship that have children");
        getOntologyRepository().deleteRelationship(SANDBOX_RELATIONSHIP_IRI, adminUser, workspaceId);
    }

    @Test
    public void testDeletingSandboxedRelationships() throws Exception {
        createSampleOntology();
        Relationship relationship = getOntologyRepository().getRelationshipByIRI(SANDBOX_RELATIONSHIP_IRI, workspaceId);
        String propertyThatShouldBeDeleted = SANDBOX_PROPERTY_IRI + ".relationship";

        createProperty(propertyThatShouldBeDeleted, SANDBOX_DISPLAY_NAME, Arrays.asList(), Arrays.asList(relationship), workspaceId);
        getOntologyRepository().clearCache(workspaceId);

        relationship = getOntologyRepository().getRelationshipByIRI(SANDBOX_RELATIONSHIP_IRI, workspaceId);

        OntologyProperty property = getOntologyRepository().getPropertyByIRI(propertyThatShouldBeDeleted, workspaceId);
        assertTrue("Property exists", property != null && property.getIri().equals(propertyThatShouldBeDeleted));
        assertTrue("Relationship has property", relationship.getProperties().stream().anyMatch(ontologyProperty -> ontologyProperty.getIri().equals(propertyThatShouldBeDeleted)));

        getOntologyRepository().deleteRelationship(SANDBOX_RELATIONSHIP_IRI, adminUser, workspaceId);
        getOntologyRepository().clearCache(workspaceId);

        relationship = getOntologyRepository().getRelationshipByIRI(SANDBOX_RELATIONSHIP_IRI, workspaceId);
        assertTrue("Relationship should have been deleted", relationship == null);

        property = getOntologyRepository().getPropertyByIRI(propertyThatShouldBeDeleted, workspaceId);
        assertTrue("Property only used in this relationship is deleted", property == null);

        property = getOntologyRepository().getPropertyByIRI(SANDBOX_PROPERTY_IRI, workspaceId);
        assertTrue("Property used in other relationships is updated", property != null);
        assertEquals(1, property.getRelationshipIris().size());
        assertEquals(PUBLIC_RELATIONSHIP_IRI, property.getRelationshipIris().get(0));
    }

    @Test
    public void testGetConceptsByIri() throws Exception {
        createSampleOntology();

        Iterable<Concept> conceptsByIRI = getOntologyRepository().getConceptsByIRI(Collections.singletonList(PUBLIC_CONCEPT_IRI), PUBLIC);
        List<Concept> concepts = IterableUtils.toList(conceptsByIRI);
        assertEquals(1, concepts.size());
        assertEquals(PUBLIC_CONCEPT_IRI, concepts.get(0).getIRI());

        conceptsByIRI = getOntologyRepository().getConceptsByIRI(Collections.singletonList(SANDBOX_CONCEPT_IRI), workspaceId);
        concepts = IterableUtils.toList(conceptsByIRI);
        assertEquals(1, concepts.size());
        assertEquals(SANDBOX_CONCEPT_IRI, concepts.get(0).getIRI());

        conceptsByIRI = getOntologyRepository().getConceptsByIRI(Arrays.asList(PUBLIC_CONCEPT_IRI, SANDBOX_CONCEPT_IRI), PUBLIC);
        concepts = IterableUtils.toList(conceptsByIRI);
        assertEquals(1, concepts.size());
        assertEquals(PUBLIC_CONCEPT_IRI, concepts.get(0).getIRI());

        conceptsByIRI = getOntologyRepository().getConceptsByIRI(Arrays.asList(PUBLIC_CONCEPT_IRI, SANDBOX_CONCEPT_IRI), workspaceId);
        concepts = IterableUtils.toList(conceptsByIRI);
        assertEquals(2, concepts.size());
        assertTrue(concepts.stream().map(Concept::getIRI).anyMatch(iri -> iri.equals(PUBLIC_CONCEPT_IRI)));
        assertTrue(concepts.stream().map(Concept::getIRI).anyMatch(iri -> iri.equals(SANDBOX_CONCEPT_IRI)));
    }

    @Test
    public void testGetConceptsById() throws Exception {
        SampleOntologyDetails sampleOntologyDetails = createSampleOntology();

        Iterable<Concept> conceptsByIRI = getOntologyRepository().getConcepts(Collections.singletonList(sampleOntologyDetails.publicConceptId), PUBLIC);
        List<Concept> concepts = IterableUtils.toList(conceptsByIRI);
        assertEquals(1, concepts.size());
        assertEquals(PUBLIC_CONCEPT_IRI, concepts.get(0).getIRI());

        conceptsByIRI = getOntologyRepository().getConcepts(Collections.singletonList(sampleOntologyDetails.sandboxConceptId), workspaceId);
        concepts = IterableUtils.toList(conceptsByIRI);
        assertEquals(1, concepts.size());
        assertEquals(SANDBOX_CONCEPT_IRI, concepts.get(0).getIRI());

        conceptsByIRI = getOntologyRepository().getConcepts(Arrays.asList(sampleOntologyDetails.publicConceptId, sampleOntologyDetails.sandboxConceptId), PUBLIC);
        concepts = IterableUtils.toList(conceptsByIRI);
        assertEquals(1, concepts.size());
        assertEquals(PUBLIC_CONCEPT_IRI, concepts.get(0).getIRI());

        conceptsByIRI = getOntologyRepository().getConcepts(Arrays.asList(sampleOntologyDetails.publicConceptId, sampleOntologyDetails.sandboxConceptId), workspaceId);
        concepts = IterableUtils.toList(conceptsByIRI);
        assertEquals(2, concepts.size());
        assertTrue(concepts.stream().map(Concept::getIRI).anyMatch(iri -> iri.equals(PUBLIC_CONCEPT_IRI)));
        assertTrue(concepts.stream().map(Concept::getIRI).anyMatch(iri -> iri.equals(SANDBOX_CONCEPT_IRI)));
    }

    @Test
    public void testGetRelationshipsByIri() throws Exception {
        createSampleOntology();

        Iterable<Relationship> relationshipsByIRI = getOntologyRepository().getRelationshipsByIRI(Collections.singletonList(PUBLIC_RELATIONSHIP_IRI), PUBLIC);
        List<Relationship> relationships = IterableUtils.toList(relationshipsByIRI);
        assertEquals(1, relationships.size());
        assertEquals(PUBLIC_RELATIONSHIP_IRI, relationships.get(0).getIRI());

        relationshipsByIRI = getOntologyRepository().getRelationshipsByIRI(Collections.singletonList(SANDBOX_RELATIONSHIP_IRI), workspaceId);
        relationships = IterableUtils.toList(relationshipsByIRI);
        assertEquals(1, relationships.size());
        assertEquals(SANDBOX_RELATIONSHIP_IRI, relationships.get(0).getIRI());

        relationshipsByIRI = getOntologyRepository().getRelationshipsByIRI(Arrays.asList(PUBLIC_RELATIONSHIP_IRI, SANDBOX_RELATIONSHIP_IRI), PUBLIC);
        relationships = IterableUtils.toList(relationshipsByIRI);
        assertEquals(1, relationships.size());
        assertEquals(PUBLIC_RELATIONSHIP_IRI, relationships.get(0).getIRI());

        relationshipsByIRI = getOntologyRepository().getRelationshipsByIRI(Arrays.asList(PUBLIC_RELATIONSHIP_IRI, SANDBOX_RELATIONSHIP_IRI), workspaceId);
        relationships = IterableUtils.toList(relationshipsByIRI);
        assertEquals(2, relationships.size());
        assertTrue(relationships.stream().map(Relationship::getIRI).anyMatch(iri -> iri.equals(PUBLIC_RELATIONSHIP_IRI)));
        assertTrue(relationships.stream().map(Relationship::getIRI).anyMatch(iri -> iri.equals(SANDBOX_RELATIONSHIP_IRI)));
    }

    @Test
    public void testGetRelationshipsById() throws Exception {
        SampleOntologyDetails sampleOntologyDetails = createSampleOntology();

        Iterable<Relationship> relationshipsByIRI = getOntologyRepository().getRelationships(Collections.singletonList(sampleOntologyDetails.publicRelationshipId), PUBLIC);
        List<Relationship> relationships = IterableUtils.toList(relationshipsByIRI);
        assertEquals(1, relationships.size());
        assertEquals(PUBLIC_RELATIONSHIP_IRI, relationships.get(0).getIRI());

        relationshipsByIRI = getOntologyRepository().getRelationships(Collections.singletonList(sampleOntologyDetails.sandboxRelationshipId), workspaceId);
        relationships = IterableUtils.toList(relationshipsByIRI);
        assertEquals(1, relationships.size());
        assertEquals(SANDBOX_RELATIONSHIP_IRI, relationships.get(0).getIRI());

        relationshipsByIRI = getOntologyRepository().getRelationships(Arrays.asList(sampleOntologyDetails.publicRelationshipId, sampleOntologyDetails.sandboxRelationshipId), PUBLIC);
        relationships = IterableUtils.toList(relationshipsByIRI);
        assertEquals(1, relationships.size());
        assertEquals(PUBLIC_RELATIONSHIP_IRI, relationships.get(0).getIRI());

        relationshipsByIRI = getOntologyRepository().getRelationships(Arrays.asList(sampleOntologyDetails.publicRelationshipId, sampleOntologyDetails.sandboxRelationshipId), workspaceId);
        relationships = IterableUtils.toList(relationshipsByIRI);
        assertEquals(2, relationships.size());
        assertTrue(relationships.stream().map(Relationship::getIRI).anyMatch(iri -> iri.equals(PUBLIC_RELATIONSHIP_IRI)));
        assertTrue(relationships.stream().map(Relationship::getIRI).anyMatch(iri -> iri.equals(SANDBOX_RELATIONSHIP_IRI)));
    }

    @Test
    public void testPropertiesByIri() throws Exception {
        createSampleOntology();

        Iterable<OntologyProperty> propertiesByIRI = getOntologyRepository().getPropertiesByIRI(Collections.singletonList(PUBLIC_PROPERTY_IRI), PUBLIC);
        List<OntologyProperty> properties = IterableUtils.toList(propertiesByIRI);
        assertEquals(1, properties.size());
        assertEquals(PUBLIC_PROPERTY_IRI, properties.get(0).getIri());

        propertiesByIRI = getOntologyRepository().getPropertiesByIRI(Collections.singletonList(SANDBOX_PROPERTY_IRI), workspaceId);
        properties = IterableUtils.toList(propertiesByIRI);
        assertEquals(1, properties.size());
        assertEquals(SANDBOX_PROPERTY_IRI, properties.get(0).getIri());

        propertiesByIRI = getOntologyRepository().getPropertiesByIRI(Arrays.asList(PUBLIC_PROPERTY_IRI, SANDBOX_PROPERTY_IRI), PUBLIC);
        properties = IterableUtils.toList(propertiesByIRI);
        assertEquals(1, properties.size());
        assertEquals(PUBLIC_PROPERTY_IRI, properties.get(0).getIri());

        propertiesByIRI = getOntologyRepository().getPropertiesByIRI(Arrays.asList(PUBLIC_PROPERTY_IRI, SANDBOX_PROPERTY_IRI), workspaceId);
        properties = IterableUtils.toList(propertiesByIRI);
        assertEquals(2, properties.size());
        assertTrue(properties.stream().map(OntologyProperty::getIri).anyMatch(iri -> iri.equals(PUBLIC_PROPERTY_IRI)));
        assertTrue(properties.stream().map(OntologyProperty::getIri).anyMatch(iri -> iri.equals(SANDBOX_PROPERTY_IRI)));
    }

    @Test
    public void testPropertiesById() throws Exception {
        SampleOntologyDetails sampleOntologyDetails = createSampleOntology();

        Iterable<OntologyProperty> propertiesByIRI = getOntologyRepository().getProperties(Collections.singletonList(sampleOntologyDetails.publicPropertyId), PUBLIC);
        List<OntologyProperty> properties = IterableUtils.toList(propertiesByIRI);
        assertEquals(1, properties.size());
        assertEquals(PUBLIC_PROPERTY_IRI, properties.get(0).getIri());

        propertiesByIRI = getOntologyRepository().getProperties(Collections.singletonList(sampleOntologyDetails.sandboxPropertyId), workspaceId);
        properties = IterableUtils.toList(propertiesByIRI);
        assertEquals(1, properties.size());
        assertEquals(SANDBOX_PROPERTY_IRI, properties.get(0).getIri());

        propertiesByIRI = getOntologyRepository().getProperties(Arrays.asList(sampleOntologyDetails.publicPropertyId, sampleOntologyDetails.sandboxPropertyId), PUBLIC);
        properties = IterableUtils.toList(propertiesByIRI);
        assertEquals(1, properties.size());
        assertEquals(PUBLIC_PROPERTY_IRI, properties.get(0).getIri());

        propertiesByIRI = getOntologyRepository().getProperties(Arrays.asList(sampleOntologyDetails.publicPropertyId, sampleOntologyDetails.sandboxPropertyId), workspaceId);
        properties = IterableUtils.toList(propertiesByIRI);
        assertEquals(2, properties.size());
        assertTrue(properties.stream().map(OntologyProperty::getIri).anyMatch(iri -> iri.equals(PUBLIC_PROPERTY_IRI)));
        assertTrue(properties.stream().map(OntologyProperty::getIri).anyMatch(iri -> iri.equals(SANDBOX_PROPERTY_IRI)));
    }

    @Test
    public void testClientApiObjectWithUnknownWorkspace() throws Exception {
        createSampleOntology();

        ClientApiOntology clientApiObject = getOntologyRepository().getClientApiObject("unknown-workspace");

        assertFalse(clientApiObject.getConcepts().stream().anyMatch(concept -> concept.getTitle().equals(SANDBOX_CONCEPT_IRI)));
        ClientApiOntology.Concept publicApiConcept = clientApiObject.getConcepts().stream()
                .filter(concept -> concept.getTitle().equals(PUBLIC_CONCEPT_IRI)).findFirst()
                .orElseThrow(() -> new VisalloException("Unable to load public concept"));
        assertEquals(SandboxStatus.PUBLIC, publicApiConcept.getSandboxStatus());

        assertFalse(clientApiObject.getRelationships().stream().anyMatch(relationship -> relationship.getTitle().equals(SANDBOX_RELATIONSHIP_IRI)));
        ClientApiOntology.Relationship publicApiRelationship = clientApiObject.getRelationships().stream()
                .filter(relationship -> relationship.getTitle().equals(PUBLIC_RELATIONSHIP_IRI)).findFirst()
                .orElseThrow(() -> new VisalloException("Unable to load public relationship"));
        assertEquals(SandboxStatus.PUBLIC, publicApiRelationship.getSandboxStatus());

        assertFalse(clientApiObject.getProperties().stream().anyMatch(property -> property.getTitle().equals(SANDBOX_PROPERTY_IRI)));
        ClientApiOntology.Property publicApiProperty = clientApiObject.getProperties().stream()
                .filter(property -> property.getTitle().equals(PUBLIC_PROPERTY_IRI)).findFirst()
                .orElseThrow(() -> new VisalloException("Unable to load public property"));
        assertEquals(SandboxStatus.PUBLIC, publicApiProperty.getSandboxStatus());
    }

    @Test
    public void testClientApiObjectWithNoWorkspace() throws Exception {
        createSampleOntology();

        ClientApiOntology clientApiObject = getOntologyRepository().getClientApiObject(PUBLIC);

        assertFalse(clientApiObject.getConcepts().stream().anyMatch(concept -> concept.getTitle().equals(SANDBOX_CONCEPT_IRI)));
        ClientApiOntology.Concept publicApiConcept = clientApiObject.getConcepts().stream()
                .filter(concept -> concept.getTitle().equals(PUBLIC_CONCEPT_IRI)).findFirst()
                .orElseThrow(() -> new VisalloException("Unable to load public concept"));
        assertEquals(SandboxStatus.PUBLIC, publicApiConcept.getSandboxStatus());

        assertFalse(clientApiObject.getRelationships().stream().anyMatch(relationship -> relationship.getTitle().equals(SANDBOX_RELATIONSHIP_IRI)));
        ClientApiOntology.Relationship publicApiRelationship = clientApiObject.getRelationships().stream()
                .filter(relationship -> relationship.getTitle().equals(PUBLIC_RELATIONSHIP_IRI)).findFirst()
                .orElseThrow(() -> new VisalloException("Unable to load public relationship"));
        assertEquals(SandboxStatus.PUBLIC, publicApiRelationship.getSandboxStatus());

        assertFalse(clientApiObject.getProperties().stream().anyMatch(property -> property.getTitle().equals(SANDBOX_PROPERTY_IRI)));
        ClientApiOntology.Property publicApiProperty = clientApiObject.getProperties().stream()
                .filter(property -> property.getTitle().equals(PUBLIC_PROPERTY_IRI)).findFirst()
                .orElseThrow(() -> new VisalloException("Unable to load public property"));
        assertEquals(SandboxStatus.PUBLIC, publicApiProperty.getSandboxStatus());

        // ensure the sandboxed property appears on all the proper components
        assertEquals(1, publicApiConcept.getProperties().size());
        assertEquals(PUBLIC_PROPERTY_IRI, publicApiConcept.getProperties().get(0));
        assertEquals(1, publicApiRelationship.getProperties().size());
        assertEquals(PUBLIC_PROPERTY_IRI, publicApiRelationship.getProperties().get(0));
    }

    @Test
    public void testClientApiObjectWithValidWorkspace() throws Exception {
        createSampleOntology();

        ClientApiOntology clientApiObject = getOntologyRepository().getClientApiObject(workspaceId);

        ClientApiOntology.Concept sandboxApiConcept = clientApiObject.getConcepts().stream()
                .filter(concept -> concept.getTitle().equals(SANDBOX_CONCEPT_IRI)).findFirst()
                .orElseThrow(() -> new VisalloException("Unable to load sandbox concept"));
        assertEquals(SandboxStatus.PRIVATE, sandboxApiConcept.getSandboxStatus());
        ClientApiOntology.Concept publicApiConcept = clientApiObject.getConcepts().stream()
                .filter(concept -> concept.getTitle().equals(PUBLIC_CONCEPT_IRI)).findFirst()
                .orElseThrow(() -> new VisalloException("Unable to load public concept"));
        assertEquals(SandboxStatus.PUBLIC, publicApiConcept.getSandboxStatus());

        ClientApiOntology.Relationship sandboxApiRelationship = clientApiObject.getRelationships().stream()
                .filter(relationship -> relationship.getTitle().equals(SANDBOX_RELATIONSHIP_IRI)).findFirst()
                .orElseThrow(() -> new VisalloException("Unable to load sandbox relationship"));
        assertEquals(SandboxStatus.PRIVATE, sandboxApiRelationship.getSandboxStatus());
        ClientApiOntology.Relationship publicApiRelationship = clientApiObject.getRelationships().stream()
                .filter(relationship -> relationship.getTitle().equals(PUBLIC_RELATIONSHIP_IRI)).findFirst()
                .orElseThrow(() -> new VisalloException("Unable to load public relationship"));
        assertEquals(SandboxStatus.PUBLIC, publicApiRelationship.getSandboxStatus());

        ClientApiOntology.Property sandboxApiProperty = clientApiObject.getProperties().stream()
                .filter(property -> property.getTitle().equals(SANDBOX_PROPERTY_IRI)).findFirst()
                .orElseThrow(() -> new VisalloException("Unable to load sandbox property"));
        assertEquals(SandboxStatus.PRIVATE, sandboxApiProperty.getSandboxStatus());
        ClientApiOntology.Property publicApiProperty = clientApiObject.getProperties().stream()
                .filter(property -> property.getTitle().equals(PUBLIC_PROPERTY_IRI)).findFirst()
                .orElseThrow(() -> new VisalloException("Unable to load public property"));
        assertEquals(SandboxStatus.PUBLIC, publicApiProperty.getSandboxStatus());

        // ensure the sandboxed property appears on all the proper components
        assertEquals(2, publicApiConcept.getProperties().size());
        assertTrue(publicApiConcept.getProperties().stream().anyMatch(p -> p.equals(PUBLIC_PROPERTY_IRI)));
        assertTrue(publicApiConcept.getProperties().stream().anyMatch(p -> p.equals(SANDBOX_PROPERTY_IRI)));
        assertEquals(2, publicApiRelationship.getProperties().size());
        assertTrue(publicApiRelationship.getProperties().stream().anyMatch(p -> p.equals(PUBLIC_PROPERTY_IRI)));
        assertTrue(publicApiRelationship.getProperties().stream().anyMatch(p -> p.equals(SANDBOX_PROPERTY_IRI)));
        assertEquals(2, sandboxApiConcept.getProperties().size());
        assertEquals(SANDBOX_PROPERTY_IRI, sandboxApiConcept.getProperties().get(0));
        assertEquals(1, sandboxApiRelationship.getProperties().size());
        assertEquals(SANDBOX_PROPERTY_IRI, sandboxApiRelationship.getProperties().get(0));
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testCreatingConceptsWithNoUserOrWorkspace() {
        Concept thing = getOntologyRepository().getEntityConcept(workspaceId);
        getOntologyRepository().getOrCreateConcept(thing, PUBLIC_CONCEPT_IRI, PUBLIC_DISPLAY_NAME, null, null, PUBLIC);
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testCreatingPublicConceptsWithoutPublishPrivilege() {
        Concept thing = getOntologyRepository().getEntityConcept(workspaceId);
        getOntologyRepository().getOrCreateConcept(thing, PUBLIC_CONCEPT_IRI, PUBLIC_DISPLAY_NAME, null, user, PUBLIC);
    }

    @Test
    public void testCreatingPublicConcepts() {
        setPrivileges(user, Sets.newHashSet(Privilege.ONTOLOGY_ADD, Privilege.ONTOLOGY_PUBLISH));

        Concept thing = getOntologyRepository().getEntityConcept(workspaceId);
        getOntologyRepository().getOrCreateConcept(thing, PUBLIC_CONCEPT_IRI, PUBLIC_DISPLAY_NAME, null, user, PUBLIC);
        getOntologyRepository().clearCache();

        Concept noWorkspace = getOntologyRepository().getConceptByIRI(PUBLIC_CONCEPT_IRI, PUBLIC);
        assertEquals(PUBLIC_DISPLAY_NAME, noWorkspace.getDisplayName());

        Concept withWorkspace = getOntologyRepository().getConceptByIRI(PUBLIC_CONCEPT_IRI, workspaceId);
        assertEquals(PUBLIC_DISPLAY_NAME, withWorkspace.getDisplayName());
    }

    @Test
    public void testCreatingPublicConceptsAsSystem() {
        Concept thing = getOntologyRepository().getEntityConcept(workspaceId);
        getOntologyRepository().getOrCreateConcept(thing, PUBLIC_CONCEPT_IRI, PUBLIC_DISPLAY_NAME, null, systemUser, PUBLIC);
        getOntologyRepository().clearCache();

        Concept noWorkspace = getOntologyRepository().getConceptByIRI(PUBLIC_CONCEPT_IRI, PUBLIC);
        assertEquals(PUBLIC_DISPLAY_NAME, noWorkspace.getDisplayName());

        Concept withWorkspace = getOntologyRepository().getConceptByIRI(PUBLIC_CONCEPT_IRI, workspaceId);
        assertEquals(PUBLIC_DISPLAY_NAME, withWorkspace.getDisplayName());
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testCreatingSandboxedConceptsWithoutAddPermissionPrivilege() {
        Concept thing = getOntologyRepository().getEntityConcept(workspaceId);
        getOntologyRepository().getOrCreateConcept(thing, SANDBOX_CONCEPT_IRI, SANDBOX_DISPLAY_NAME, null, user, workspaceId);
    }

    @Test
    public void testCreatingSandboxedConcepts() {
        setPrivileges(user, Collections.singleton(Privilege.ONTOLOGY_ADD));

        Concept thing = getOntologyRepository().getEntityConcept(workspaceId);
        getOntologyRepository().getOrCreateConcept(thing, SANDBOX_CONCEPT_IRI, SANDBOX_DISPLAY_NAME, null, user, workspaceId);
        getOntologyRepository().clearCache();

        Concept noWorkspace = getOntologyRepository().getConceptByIRI(SANDBOX_CONCEPT_IRI, PUBLIC);
        assertNull(noWorkspace);

        Concept withWorkspace = getOntologyRepository().getConceptByIRI(SANDBOX_CONCEPT_IRI, workspaceId);
        assertEquals(SANDBOX_DISPLAY_NAME, withWorkspace.getDisplayName());
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testPublishingConceptsWithoutPublishPrivilege() {
        setPrivileges(user, Collections.singleton(Privilege.ONTOLOGY_ADD));

        Concept thing = getOntologyRepository().getEntityConcept(workspaceId);
        Concept sandboxedConcept = getOntologyRepository().getOrCreateConcept(thing, SANDBOX_CONCEPT_IRI, SANDBOX_DISPLAY_NAME, null, user, workspaceId);
        getOntologyRepository().clearCache();

        getOntologyRepository().publishConcept(sandboxedConcept, user, workspaceId);
    }

    @Test
    public void testPublishingConcepts() {
        setPrivileges(user, Sets.newHashSet(Privilege.ONTOLOGY_ADD, Privilege.ONTOLOGY_PUBLISH));

        Concept thing = getOntologyRepository().getEntityConcept(workspaceId);
        Concept sandboxedConcept = getOntologyRepository().getOrCreateConcept(thing, SANDBOX_CONCEPT_IRI, SANDBOX_DISPLAY_NAME, null, user, workspaceId);
        getOntologyRepository().publishConcept(sandboxedConcept, user, workspaceId);
        getOntologyRepository().clearCache();

        Concept publicConcept = getOntologyRepository().getConceptByIRI(SANDBOX_CONCEPT_IRI, PUBLIC);
        assertEquals(SANDBOX_DISPLAY_NAME, publicConcept.getDisplayName());
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testCreatingRelationshipsWithNoUserOrWorkspace() {
        List<Concept> thing = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        getOntologyRepository().getOrCreateRelationshipType(null, thing, thing, PUBLIC_RELATIONSHIP_IRI, null, true, null, PUBLIC);
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testCreatingPublicRelationshipsWithoutPublishPrivilege() {
        List<Concept> thing = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        getOntologyRepository().getOrCreateRelationshipType(null, thing, thing, PUBLIC_RELATIONSHIP_IRI, null, true, user, PUBLIC);
    }

    @Test
    public void testCreatingPublicRelationships() {
        setPrivileges(user, Sets.newHashSet(Privilege.ONTOLOGY_ADD, Privilege.ONTOLOGY_PUBLISH));

        List<Concept> thing = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        getOntologyRepository().getOrCreateRelationshipType(null, thing, thing, PUBLIC_RELATIONSHIP_IRI, null, true, user, PUBLIC);
        getOntologyRepository().clearCache();

        Relationship noWorkspace = getOntologyRepository().getRelationshipByIRI(PUBLIC_RELATIONSHIP_IRI, PUBLIC);
        assertEquals(PUBLIC_RELATIONSHIP_IRI, noWorkspace.getIRI());

        Relationship withWorkspace = getOntologyRepository().getRelationshipByIRI(PUBLIC_RELATIONSHIP_IRI, workspaceId);
        assertEquals(PUBLIC_RELATIONSHIP_IRI, withWorkspace.getIRI());
    }

    @Test
    public void testCreatingPublicRelationshipsAsSystem() {
        List<Concept> thing = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        getOntologyRepository().getOrCreateRelationshipType(null, thing, thing, PUBLIC_RELATIONSHIP_IRI, null, true, systemUser, PUBLIC);
        getOntologyRepository().clearCache();

        Relationship noWorkspace = getOntologyRepository().getRelationshipByIRI(PUBLIC_RELATIONSHIP_IRI, PUBLIC);
        assertEquals(PUBLIC_RELATIONSHIP_IRI, noWorkspace.getIRI());

        Relationship withWorkspace = getOntologyRepository().getRelationshipByIRI(PUBLIC_RELATIONSHIP_IRI, workspaceId);
        assertEquals(PUBLIC_RELATIONSHIP_IRI, withWorkspace.getIRI());
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testCreatingSandboxedRelationshipsWithoutAddPermissionPrivilege() {
        List<Concept> thing = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        getOntologyRepository().getOrCreateRelationshipType(null, thing, thing, SANDBOX_RELATIONSHIP_IRI, null, true, user, workspaceId);
    }

    @Test
    public void testCreatingSandboxedRelationships() {
        setPrivileges(user, Collections.singleton(Privilege.ONTOLOGY_ADD));

        List<Concept> thing = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        getOntologyRepository().getOrCreateRelationshipType(null, thing, thing, SANDBOX_RELATIONSHIP_IRI, null, true, user, workspaceId);
        getOntologyRepository().clearCache();

        Relationship noWorkspace = getOntologyRepository().getRelationshipByIRI(SANDBOX_RELATIONSHIP_IRI, PUBLIC);
        assertNull(noWorkspace);

        Relationship withWorkspace = getOntologyRepository().getRelationshipByIRI(SANDBOX_RELATIONSHIP_IRI, workspaceId);
        assertEquals(SANDBOX_RELATIONSHIP_IRI, withWorkspace.getIRI());
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testPublishingRelationshipsWithoutPublishPrivilege() {
        setPrivileges(user, Collections.singleton(Privilege.ONTOLOGY_ADD));

        List<Concept> thing = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        Relationship sandboxedRelationship = getOntologyRepository().getOrCreateRelationshipType(null, thing, thing, SANDBOX_RELATIONSHIP_IRI, null, true, user, workspaceId);
        getOntologyRepository().publishRelationship(sandboxedRelationship, user, workspaceId);
    }

    @Test
    public void testPublishingRelationships() {
        setPrivileges(user, Sets.newHashSet(Privilege.ONTOLOGY_ADD, Privilege.ONTOLOGY_PUBLISH));

        List<Concept> thing = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        Relationship sandboxedRelationship = getOntologyRepository().getOrCreateRelationshipType(null, thing, thing, SANDBOX_RELATIONSHIP_IRI, null, true, user, workspaceId);
        getOntologyRepository().publishRelationship(sandboxedRelationship, user, workspaceId);
        getOntologyRepository().clearCache();

        Relationship publicRelationship = getOntologyRepository().getRelationshipByIRI(SANDBOX_RELATIONSHIP_IRI, PUBLIC);
        assertEquals(SANDBOX_RELATIONSHIP_IRI, publicRelationship.getIRI());
    }

    @Test
    public void testAddingPublicConceptsToPublicRelationships() {
        setPrivileges(user, Collections.singleton(Privilege.ONTOLOGY_ADD));

        getWorkspaceRepository().add(workspaceId, "Junit Workspace", user);

        Concept publicConcept = createConcept(PUBLIC_CONCEPT_IRI, PUBLIC_DISPLAY_NAME, PUBLIC);
        Concept publicConceptB = createConcept(PUBLIC_CONCEPT_IRI + 'b', PUBLIC_DISPLAY_NAME, PUBLIC);
        createRelationship(PUBLIC_RELATIONSHIP_IRI, PUBLIC);

        getOntologyRepository().clearCache();

        try {
            getOntologyRepository().addDomainConceptsToRelationshipType(PUBLIC_RELATIONSHIP_IRI, Collections.singletonList(publicConcept.getIRI()), systemUser, workspaceId);
            fail();
        } catch (UnsupportedOperationException uoe) {
            // this shouldn't be supported yet
        }
        try {
            getOntologyRepository().addRangeConceptsToRelationshipType(PUBLIC_RELATIONSHIP_IRI, Collections.singletonList(publicConceptB.getIRI()), systemUser, workspaceId);
            fail();
        } catch (UnsupportedOperationException uoe) {
            // this shouldn't be supported yet
        }
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testCreatingPropertyWithNoUserOrWorkspace() {
        List<Concept> thing = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        OntologyPropertyDefinition ontologyPropertyDefinition = new OntologyPropertyDefinition(thing, PUBLIC_PROPERTY_IRI, SANDBOX_DISPLAY_NAME, PropertyType.DATE);
        getOntologyRepository().getOrCreateProperty(ontologyPropertyDefinition, null, PUBLIC);
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testCreatingPublicPropertyWithoutPublishPrivilege() {
        List<Concept> thing = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        OntologyPropertyDefinition ontologyPropertyDefinition = new OntologyPropertyDefinition(thing, PUBLIC_PROPERTY_IRI, SANDBOX_DISPLAY_NAME, PropertyType.DATE);
        getOntologyRepository().getOrCreateProperty(ontologyPropertyDefinition, user, PUBLIC);
    }

    @Test
    public void testCreatingPublicProperty() {
        setPrivileges(user, Sets.newHashSet(Privilege.ONTOLOGY_ADD, Privilege.ONTOLOGY_PUBLISH));

        List<Concept> thing = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        OntologyPropertyDefinition ontologyPropertyDefinition = new OntologyPropertyDefinition(thing, PUBLIC_PROPERTY_IRI, SANDBOX_DISPLAY_NAME, PropertyType.DATE);
        getOntologyRepository().getOrCreateProperty(ontologyPropertyDefinition, user, PUBLIC);
        getOntologyRepository().clearCache();

        OntologyProperty noWorkspace = getOntologyRepository().getPropertyByIRI(PUBLIC_PROPERTY_IRI, PUBLIC);
        assertEquals(PUBLIC_PROPERTY_IRI, noWorkspace.getIri());

        OntologyProperty withWorkspace = getOntologyRepository().getPropertyByIRI(PUBLIC_PROPERTY_IRI, workspaceId);
        assertEquals(PUBLIC_PROPERTY_IRI, withWorkspace.getIri());
    }

    @Test
    public void testCreatingPublicPropertyAsSystem() {
        List<Concept> thing = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        OntologyPropertyDefinition ontologyPropertyDefinition = new OntologyPropertyDefinition(thing, PUBLIC_PROPERTY_IRI, SANDBOX_DISPLAY_NAME, PropertyType.DATE);
        getOntologyRepository().getOrCreateProperty(ontologyPropertyDefinition, systemUser, PUBLIC);
        getOntologyRepository().clearCache();

        OntologyProperty noWorkspace = getOntologyRepository().getPropertyByIRI(PUBLIC_PROPERTY_IRI, PUBLIC);
        assertEquals(PUBLIC_PROPERTY_IRI, noWorkspace.getIri());

        OntologyProperty withWorkspace = getOntologyRepository().getPropertyByIRI(PUBLIC_PROPERTY_IRI, workspaceId);
        assertEquals(PUBLIC_PROPERTY_IRI, withWorkspace.getIri());
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testCreatingSandboxedPropertyWithoutAddPermissionPrivilege() {
        List<Concept> thing = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        OntologyPropertyDefinition ontologyPropertyDefinition = new OntologyPropertyDefinition(thing, SANDBOX_PROPERTY_IRI, SANDBOX_DISPLAY_NAME, PropertyType.DATE);
        getOntologyRepository().getOrCreateProperty(ontologyPropertyDefinition, user, workspaceId);
    }

    @Test
    public void testCreatingSandboxedProperty() {
        setPrivileges(user, Collections.singleton(Privilege.ONTOLOGY_ADD));

        List<Concept> things = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        Relationship publicRelationship = getOntologyRepository().getOrCreateRelationshipType(null, things, things, PUBLIC_RELATIONSHIP_IRI, true, systemUser, PUBLIC);

        OntologyPropertyDefinition ontologyPropertyDefinition = new OntologyPropertyDefinition(
                things,
                Collections.singletonList(publicRelationship),
                SANDBOX_PROPERTY_IRI, SANDBOX_DISPLAY_NAME, PropertyType.DATE);
        getOntologyRepository().getOrCreateProperty(ontologyPropertyDefinition, user, workspaceId);
        getOntologyRepository().clearCache();

        OntologyProperty noWorkspace = getOntologyRepository().getPropertyByIRI(SANDBOX_PROPERTY_IRI, PUBLIC);
        assertNull(noWorkspace);

        Concept thing = getOntologyRepository().getEntityConcept(PUBLIC);
        publicRelationship = getOntologyRepository().getRelationshipByIRI(PUBLIC_RELATIONSHIP_IRI, PUBLIC);
        assertEquals(0, thing.getProperties().size());
        assertEquals(0, publicRelationship.getProperties().size());

        OntologyProperty withWorkspace = getOntologyRepository().getPropertyByIRI(SANDBOX_PROPERTY_IRI, workspaceId);
        assertEquals(SANDBOX_PROPERTY_IRI, withWorkspace.getIri());

        thing = getOntologyRepository().getEntityConcept(workspaceId);
        publicRelationship = getOntologyRepository().getRelationshipByIRI(PUBLIC_RELATIONSHIP_IRI, workspaceId);
        assertEquals(1, thing.getProperties().size());
        assertEquals(SANDBOX_PROPERTY_IRI, thing.getProperties().iterator().next().getIri());
        assertEquals(1, publicRelationship.getProperties().size());
        assertEquals(SANDBOX_PROPERTY_IRI, publicRelationship.getProperties().iterator().next().getIri());
    }

    @Test
    public void testAddingPublicPropertyToPublicConceptsAndRelationships() {
        setPrivileges(user, Collections.singleton(Privilege.ONTOLOGY_ADD));

        getWorkspaceRepository().add(workspaceId, "Junit Workspace", user);

        createConcept(PUBLIC_CONCEPT_IRI, PUBLIC_DISPLAY_NAME, PUBLIC);
        createRelationship(PUBLIC_RELATIONSHIP_IRI, PUBLIC);
        OntologyProperty publicProperty = createProperty(PUBLIC_PROPERTY_IRI, PUBLIC_DISPLAY_NAME, PUBLIC);

        getOntologyRepository().clearCache();

        try {
            getOntologyRepository().updatePropertyDomainIris(publicProperty, Sets.newHashSet(PUBLIC_CONCEPT_IRI, PUBLIC_RELATIONSHIP_IRI), systemUser, workspaceId);
            fail();
        } catch (UnsupportedOperationException uoe) {
            // this shouldn't be supported yet
        }
    }

    @Test
    public void testAddingSandboxedPropertyToPublicConceptsAndRelationships() {
        setPrivileges(user, Collections.singleton(Privilege.ONTOLOGY_ADD));

        getWorkspaceRepository().add(workspaceId, "Junit Workspace", user);

        createConcept(PUBLIC_CONCEPT_IRI, PUBLIC_DISPLAY_NAME, PUBLIC);
        createRelationship(PUBLIC_RELATIONSHIP_IRI, PUBLIC);
        OntologyProperty sandboxedProperty = createProperty(SANDBOX_PROPERTY_IRI, SANDBOX_DISPLAY_NAME, workspaceId);

        getOntologyRepository().clearCache();

        getOntologyRepository().updatePropertyDomainIris(sandboxedProperty, Sets.newHashSet(PUBLIC_CONCEPT_IRI, PUBLIC_RELATIONSHIP_IRI), systemUser, workspaceId);

        getOntologyRepository().clearCache();

        // ensure that it's there in the sandbox
        Concept publicConcept = getOntologyRepository().getConceptByIRI(PUBLIC_CONCEPT_IRI, workspaceId);
        assertEquals(1, publicConcept.getProperties().size());
        assertEquals(SANDBOX_PROPERTY_IRI, publicConcept.getProperties().iterator().next().getIri());

        Relationship publicRelationship = getOntologyRepository().getRelationshipByIRI(PUBLIC_RELATIONSHIP_IRI, workspaceId);
        assertEquals(1, publicRelationship.getProperties().size());
        assertEquals(SANDBOX_PROPERTY_IRI, publicRelationship.getProperties().iterator().next().getIri());

        // ensure that it's not there outside the sandbox
        publicConcept = getOntologyRepository().getConceptByIRI(PUBLIC_CONCEPT_IRI, PUBLIC);
        assertEquals(0, publicConcept.getProperties().size());

        publicRelationship = getOntologyRepository().getRelationshipByIRI(PUBLIC_RELATIONSHIP_IRI, PUBLIC);
        assertEquals(0, publicRelationship.getProperties().size());
    }

    @Test
    public void testAddingPublicPropertyToSandboxedConceptsAndRelationships() {
        setPrivileges(user, Collections.singleton(Privilege.ONTOLOGY_ADD));

        getWorkspaceRepository().add(workspaceId, "Junit Workspace", user);

        createConcept(SANDBOX_CONCEPT_IRI, SANDBOX_DISPLAY_NAME, workspaceId);
        createRelationship(SANDBOX_RELATIONSHIP_IRI, workspaceId);
        OntologyProperty publicProperty = createProperty(PUBLIC_PROPERTY_IRI, PUBLIC_DISPLAY_NAME, PUBLIC);

        getOntologyRepository().clearCache();

        try {
            getOntologyRepository().updatePropertyDomainIris(publicProperty, Sets.newHashSet(SANDBOX_CONCEPT_IRI, SANDBOX_RELATIONSHIP_IRI), systemUser, workspaceId);
            fail();
        } catch (UnsupportedOperationException uoe) {
            // this shouldn't be supported yet
        }
    }

    @Test
    public void testAddingSandboxedPropertyToSandboxedConceptsAndRelationships() {
        setPrivileges(user, Collections.singleton(Privilege.ONTOLOGY_ADD));

        getWorkspaceRepository().add(workspaceId, "Junit Workspace", user);

        createConcept(SANDBOX_CONCEPT_IRI, SANDBOX_DISPLAY_NAME, workspaceId);
        createRelationship(SANDBOX_RELATIONSHIP_IRI, workspaceId);
        OntologyProperty sandboxedProperty = createProperty(SANDBOX_PROPERTY_IRI, SANDBOX_DISPLAY_NAME, workspaceId);

        getOntologyRepository().clearCache();

        getOntologyRepository().updatePropertyDomainIris(sandboxedProperty, Sets.newHashSet(SANDBOX_CONCEPT_IRI, SANDBOX_RELATIONSHIP_IRI), systemUser, workspaceId);

        getOntologyRepository().clearCache();

        Concept sandboxedConcept = getOntologyRepository().getConceptByIRI(SANDBOX_CONCEPT_IRI, workspaceId);
        assertEquals(1, sandboxedConcept.getProperties().size());
        assertEquals(SANDBOX_PROPERTY_IRI, sandboxedConcept.getProperties().iterator().next().getIri());

        Relationship sandboxedRelationship = getOntologyRepository().getRelationshipByIRI(SANDBOX_RELATIONSHIP_IRI, workspaceId);
        assertEquals(1, sandboxedRelationship.getProperties().size());
        assertEquals(SANDBOX_PROPERTY_IRI, sandboxedRelationship.getProperties().iterator().next().getIri());
    }

    @Test(expected = VisalloAccessDeniedException.class)
    public void testPublishingPropertyWithoutPublishPrivilege() {
        setPrivileges(user, Collections.singleton(Privilege.ONTOLOGY_ADD));

        List<Concept> thing = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        OntologyPropertyDefinition ontologyPropertyDefinition = new OntologyPropertyDefinition(thing, SANDBOX_PROPERTY_IRI, SANDBOX_DISPLAY_NAME, PropertyType.DATE);
        OntologyProperty sandboxedProperty = getOntologyRepository().getOrCreateProperty(ontologyPropertyDefinition, user, workspaceId);
        getOntologyRepository().publishProperty(sandboxedProperty, user, workspaceId);
    }

    @Test
    public void testPublishingProperty() {
        setPrivileges(user, Sets.newHashSet(Privilege.ONTOLOGY_ADD, Privilege.ONTOLOGY_PUBLISH));

        Concept thing = getOntologyRepository().getEntityConcept(workspaceId);
        List<Concept> things = Collections.singletonList(thing);
        Relationship publicRelationship = getOntologyRepository().getOrCreateRelationshipType(null, things, things, PUBLIC_RELATIONSHIP_IRI, true, systemUser, PUBLIC);
        OntologyPropertyDefinition ontologyPropertyDefinition = new OntologyPropertyDefinition(
                things,
                Collections.singletonList(publicRelationship),
                SANDBOX_PROPERTY_IRI, SANDBOX_DISPLAY_NAME, PropertyType.DATE);
        OntologyProperty sandboxedProperty = getOntologyRepository().getOrCreateProperty(ontologyPropertyDefinition, user, workspaceId);
        getOntologyRepository().publishProperty(sandboxedProperty, user, workspaceId);
        getOntologyRepository().clearCache();

        OntologyProperty publicProperty = getOntologyRepository().getPropertyByIRI(SANDBOX_PROPERTY_IRI, PUBLIC);
        assertEquals(SANDBOX_PROPERTY_IRI, publicProperty.getIri());

        thing = getOntologyRepository().getEntityConcept(PUBLIC);
        publicRelationship = getOntologyRepository().getRelationshipByIRI(PUBLIC_RELATIONSHIP_IRI, PUBLIC);
        assertEquals(1, thing.getProperties().size());
        assertEquals(SANDBOX_PROPERTY_IRI, thing.getProperties().iterator().next().getIri());
        assertEquals(1, publicRelationship.getProperties().size());
        assertEquals(SANDBOX_PROPERTY_IRI, publicRelationship.getProperties().iterator().next().getIri());
    }

    @Test
    public void testProperlyConfiguredThingConcept() throws Exception {
        createSampleOntology();

        Concept thing = getOntologyRepository().getEntityConcept(workspaceId);
        assertNotNull(thing.getTitleFormula());
        assertNotNull(thing.getSubtitleFormula());
        assertNotNull(thing.getTimeFormula());
    }

    private SampleOntologyDetails createSampleOntology() {
        setPrivileges(user, Sets.newHashSet(Privilege.ONTOLOGY_ADD, Privilege.ONTOLOGY_PUBLISH));

        getWorkspaceRepository().add(workspaceId, "Junit Workspace", user);

        Concept publicConcept = createConcept(PUBLIC_CONCEPT_IRI, PUBLIC_DISPLAY_NAME, PUBLIC);
        Concept sandboxedConcept = createConcept(SANDBOX_CONCEPT_IRI, SANDBOX_DISPLAY_NAME, workspaceId);

        Relationship publicRelationship = createRelationship(PUBLIC_RELATIONSHIP_IRI, PUBLIC);
        Relationship sandboxedRelationship = createRelationship(SANDBOX_RELATIONSHIP_IRI, workspaceId);

        OntologyProperty publicProperty = createProperty(PUBLIC_PROPERTY_IRI, PUBLIC_DISPLAY_NAME, publicConcept, publicRelationship, PUBLIC);
        OntologyProperty sandboxedProperty = createProperty(SANDBOX_PROPERTY_IRI, SANDBOX_DISPLAY_NAME, Arrays.asList(publicConcept, sandboxedConcept), Arrays.asList(publicRelationship, sandboxedRelationship), workspaceId);

        OntologyProperty sandboxedPropertyOnlySandboxedConcept = createProperty(SANDBOX_PROPERTY_IRI_ONLY_SANDBOXED_CONCEPT, SANDBOX_DISPLAY_NAME, Arrays.asList(sandboxedConcept), Arrays.asList(), workspaceId);

        getOntologyRepository().clearCache();

        return new SampleOntologyDetails(
                publicConcept.getId(), publicRelationship.getId(), publicProperty.getId(),
                sandboxedConcept.getId(),
                sandboxedRelationship.getId(), sandboxedProperty.getId(), sandboxedPropertyOnlySandboxedConcept.getId());
    }

    private Concept createConcept(String iri, String displayName, String workspaceId) {
        Concept thing = getOntologyRepository().getEntityConcept(workspaceId);
        return getOntologyRepository().getOrCreateConcept(thing, iri, displayName, null, systemUser, workspaceId);
    }

    private Relationship createRelationship(String iri, String workspaceId) {
        List<Concept> things = Collections.singletonList(getOntologyRepository().getEntityConcept(workspaceId));
        return getOntologyRepository().getOrCreateRelationshipType(null, things, things, iri, true, systemUser, workspaceId);
    }

    private OntologyProperty createProperty(String iri, String displayName, String workspaceId) {
        Concept thing = getOntologyRepository().getEntityConcept(workspaceId);
        return createProperty(iri, displayName, Collections.singletonList(thing), Collections.emptyList(), workspaceId);
    }

    private OntologyProperty createProperty(String iri, String displayName, Concept concept, Relationship relationship, String workspaceId) {
        return createProperty(iri, displayName, Collections.singletonList(concept), Collections.singletonList(relationship), workspaceId);
    }

    private OntologyProperty createProperty(String iri, String displayName, List<Concept> concepts, List<Relationship> relationships, String workspaceId) {
        OntologyPropertyDefinition publicPropertyDefinition = new OntologyPropertyDefinition(concepts, relationships, iri, displayName, PropertyType.STRING);
        publicPropertyDefinition.setTextIndexHints(Collections.singleton(TextIndexHint.EXACT_MATCH));
        publicPropertyDefinition.setUserVisible(true);
        return getOntologyRepository().getOrCreateProperty(publicPropertyDefinition, systemUser, workspaceId);
    }

    private class SampleOntologyDetails {
        String publicConceptId;
        String publicRelationshipId;
        String publicPropertyId;

        String sandboxConceptId;
        String sandboxRelationshipId;
        String sandboxPropertyId;
        String sandboxPropertyIdSandboxedConcept;

        SampleOntologyDetails(String publicConceptId, String publicRelationshipId, String publicPropertyId, String sandboxConceptId, String sandboxRelationshipId, String sandboxPropertyId, String sandboxPropertyIdSandboxedConcept) {
            this.publicConceptId = publicConceptId;
            this.publicRelationshipId = publicRelationshipId;
            this.publicPropertyId = publicPropertyId;
            this.sandboxConceptId = sandboxConceptId;
            this.sandboxRelationshipId = sandboxRelationshipId;
            this.sandboxPropertyId = sandboxPropertyId;
            this.sandboxPropertyIdSandboxedConcept = sandboxPropertyIdSandboxedConcept;
        }
    }

    private void validateTestOwlRelationship() {
        Relationship relationship = getOntologyRepository().getRelationshipByIRI(TEST_IRI + "#personKnowsPerson", PUBLIC);
        assertEquals("Knows", relationship.getDisplayName());
        assertEquals("prop('http://visallo.org/test#firstMet') || ''", relationship.getTimeFormula());
        assertTrue(relationship.getRangeConceptIRIs().contains("http://visallo.org/test#person"));
        assertTrue(relationship.getDomainConceptIRIs().contains("http://visallo.org/test#person"));

        relationship = getOntologyRepository().getRelationshipByIRI(TEST_IRI + "#personIsRelatedToPerson", PUBLIC);
        assertEquals("Is Related To", relationship.getDisplayName());
        String[] intents = relationship.getIntents();
        assertEquals(1, intents.length);
        assertEquals("test", intents[0]);
        assertTrue(relationship.getRangeConceptIRIs().contains("http://visallo.org/test#person"));
        assertTrue(relationship.getDomainConceptIRIs().contains("http://visallo.org/test#person"));
    }

    private void validateTestOwlProperties() {
        OntologyProperty nameProperty = getOntologyRepository().getPropertyByIRI(TEST_IRI + "#name", PUBLIC);
        assertEquals("Name", nameProperty.getDisplayName());
        assertEquals(PropertyType.STRING, nameProperty.getDataType());
        assertEquals("_.compact([\n" +
                "            dependentProp('http://visallo.org/test#firstName'),\n" +
                "            dependentProp('http://visallo.org/test#middleName'),\n" +
                "            dependentProp('http://visallo.org/test#lastName')\n" +
                "            ]).join(', ')", nameProperty.getDisplayFormula().trim());
        ImmutableList<String> dependentPropertyIris = nameProperty.getDependentPropertyIris();
        assertEquals(3, dependentPropertyIris.size());
        assertTrue(dependentPropertyIris.contains("http://visallo.org/test#firstName"));
        assertTrue(dependentPropertyIris.contains("http://visallo.org/test#middleName"));
        assertTrue(dependentPropertyIris.contains("http://visallo.org/test#lastName"));
        List<String> intents = Arrays.asList(nameProperty.getIntents());
        assertEquals(1, intents.size());
        assertTrue(intents.contains("test3"));
        assertEquals(
                "dependentProp('http://visallo.org/test#lastName') && dependentProp('http://visallo.org/test#firstName')",
                nameProperty.getValidationFormula()
        );
        assertEquals("Personal Information", nameProperty.getPropertyGroup());
        assertEquals("test", nameProperty.getDisplayType());
        assertFalse(nameProperty.getAddable());
        assertFalse(nameProperty.getUpdateable());
        assertFalse(nameProperty.getDeleteable());
        Map<String, String> possibleValues = nameProperty.getPossibleValues();
        assertEquals(2, possibleValues.size());
        assertEquals("test 1", possibleValues.get("T1"));
        assertEquals("test 2", possibleValues.get("T2"));

        Concept person = getOntologyRepository().getConceptByIRI(TEST_IRI + "#person", PUBLIC);
        assertTrue(person.getProperties()
                .stream()
                .anyMatch(p -> p.getIri().equals(nameProperty.getIri()))
        );

        OntologyProperty firstMetProperty = getOntologyRepository().getPropertyByIRI(TEST_IRI + "#firstMet", PUBLIC);
        assertEquals("First Met", firstMetProperty.getDisplayName());
        assertEquals(PropertyType.DATE, firstMetProperty.getDataType());

        OntologyProperty favColorProperty = getOntologyRepository().getPropertyByIRI(TEST_IRI + "#favoriteColor", PUBLIC);
        assertEquals("Favorite Color", favColorProperty.getDisplayName());
        possibleValues = favColorProperty.getPossibleValues();
        assertEquals(2, possibleValues.size());
        assertEquals("red 1", possibleValues.get("Red"));
        assertEquals("blue 2", possibleValues.get("Blue"));

        Relationship relationship = getOntologyRepository().getRelationshipByIRI(TEST_IRI + "#personKnowsPerson", PUBLIC);
        assertTrue(relationship.getProperties()
                .stream()
                .anyMatch(p -> p.getIri().equals(firstMetProperty.getIri()))
        );
    }

    private void validateTestOwlExtendedDataTables() {
        OntologyProperty personTable = getOntologyRepository().getPropertyByIRI(TEST_IRI + "#personExtendedDataTable", PUBLIC);
        assertTrue("personTable should be an instance of " + ExtendedDataTableProperty.class.getName(), personTable instanceof ExtendedDataTableProperty);
        ExtendedDataTableProperty edtp = (ExtendedDataTableProperty) personTable;
        ImmutableList<String> columns = edtp.getTablePropertyIris();
        assertEquals(2, columns.size());
        assertTrue("", columns.contains(TEST_IRI + "#personExtendedDataTableColumn1"));
        assertTrue("", columns.contains(TEST_IRI + "#personExtendedDataTableColumn2"));
    }

    private void validateTestOwlConcepts(int expectedIriSize) throws IOException {
        Concept contact = getOntologyRepository().getConceptByIRI(TEST_IRI + "#contact", PUBLIC);
        assertEquals("Contact", contact.getDisplayName());
        assertEquals("rgb(149, 138, 218)", contact.getColor());
        assertEquals("test", contact.getDisplayType());
        List<String> intents = Arrays.asList(contact.getIntents());
        assertEquals(1, intents.size());
        assertTrue(intents.contains("face"));

        Concept person = getOntologyRepository().getConceptByIRI(TEST_IRI + "#person", PUBLIC);
        assertEquals("Person", person.getDisplayName());
        intents = Arrays.asList(person.getIntents());
        assertEquals(1, intents.size());
        assertTrue(intents.contains("person"));
        assertEquals("prop('http://visallo.org/test#birthDate') || ''", person.getTimeFormula());
        assertEquals("prop('http://visallo.org/test#name') || ''", person.getTitleFormula());

        byte[] bytes = IOUtils.toByteArray(OntologyRepositoryTestBase.class.getResourceAsStream("glyphicons_003_user@2x.png"));
        assertArrayEquals(bytes, person.getGlyphIcon());
        assertEquals("rgb(28, 137, 28)", person.getColor());

        Set<Concept> conceptAndAllChildren = getOntologyRepository().getConceptAndAllChildren(contact, PUBLIC);
        List<String> iris = Lists.newArrayList();
        conceptAndAllChildren.forEach(c -> iris.add(c.getIRI()));
        assertEquals(expectedIriSize, iris.size());
        assertTrue(iris.contains(contact.getIRI()));
        assertTrue(iris.contains(person.getIRI()));
    }

    private void validateChangedOwlRelationships() throws IOException {
        Relationship relationship = getOntologyRepository().getRelationshipByIRI(TEST_IRI + "#personKnowsPerson", PUBLIC);
        assertEquals("Person Knows Person", relationship.getDisplayName());
        assertNull(relationship.getTimeFormula());
        assertTrue(relationship.getRangeConceptIRIs().contains("http://visallo.org/test#person2"));
        assertTrue(relationship.getRangeConceptIRIs().contains("http://visallo.org/test#person"));
        assertTrue(relationship.getDomainConceptIRIs().contains("http://visallo.org/test#person"));

        Concept thing = getOntologyRepository().getEntityConcept(workspaceId);
        assertNotNull(thing.getTitleFormula());
        assertNotNull(thing.getSubtitleFormula());
        assertNotNull(thing.getTimeFormula());
    }

    private void validateChangedOwlConcepts() throws IOException {
        Concept contact = getOntologyRepository().getConceptByIRI(TEST_IRI + "#contact", PUBLIC);
        Concept person = getOntologyRepository().getConceptByIRI(TEST_IRI + "#person", PUBLIC);
        assertEquals("Person", person.getDisplayName());
        List<String> intents = Arrays.asList(person.getIntents());
        assertEquals(1, intents.size());
        assertFalse(intents.contains("person"));
        assertFalse(intents.contains("face"));
        assertTrue(intents.contains("test"));
        assertNull(person.getTimeFormula());
        assertEquals("prop('http://visallo.org/test#name') || ''", person.getTitleFormula());

        assertNull(person.getGlyphIcon());
        assertEquals("rgb(28, 137, 28)", person.getColor());

        Set<Concept> conceptAndAllChildren = getOntologyRepository().getConceptAndAllChildren(contact, PUBLIC);
        List<String> iris = Lists.newArrayList();
        conceptAndAllChildren.forEach(c -> iris.add(c.getIRI()));
        assertEquals(2, iris.size());
        assertTrue(iris.contains(contact.getIRI()));
        assertTrue(iris.contains(person.getIRI()));
    }

    private void validateChangedOwlProperties() throws IOException {
        OntologyProperty nameProperty = getOntologyRepository().getPropertyByIRI(TEST_IRI + "#name", PUBLIC);
        assertEquals("http://visallo.org/test#name", nameProperty.getDisplayName());
        assertEquals(PropertyType.STRING, nameProperty.getDataType());
        assertEquals("_.compact([\n" +
                "            dependentProp('http://visallo.org/test#firstName'),\n" +
                "            dependentProp('http://visallo.org/test#lastName')\n" +
                "            ]).join(', ')", nameProperty.getDisplayFormula().trim());
        ImmutableList<String> dependentPropertyIris = nameProperty.getDependentPropertyIris();
        assertEquals(3, dependentPropertyIris.size());
        assertTrue(dependentPropertyIris.contains("http://visallo.org/test#firstName"));
        assertTrue(dependentPropertyIris.contains("http://visallo.org/test#middleName"));
        assertTrue(dependentPropertyIris.contains("http://visallo.org/test#lastName"));
        List<String> intents = Arrays.asList(nameProperty.getIntents());
        assertEquals(1, intents.size());
        assertTrue(intents.contains("test3"));
        assertEquals(
                "dependentProp('http://visallo.org/test#lastName') && dependentProp('http://visallo.org/test#firstName')",
                nameProperty.getValidationFormula()
        );
        assertEquals("Personal Information", nameProperty.getPropertyGroup());
        assertEquals("test 2", nameProperty.getDisplayType());
        assertTrue(nameProperty.getAddable());
        assertTrue(nameProperty.getUpdateable());
        assertTrue(nameProperty.getDeleteable());
        Map<String, String> possibleValues = nameProperty.getPossibleValues();
        assertNull(possibleValues);

        OntologyProperty firstMetProperty = getOntologyRepository().getPropertyByIRI(TEST_IRI + "#firstMet", PUBLIC);
        assertEquals("First Met", firstMetProperty.getDisplayName());
        assertEquals(PropertyType.DATE, firstMetProperty.getDataType());

        OntologyProperty favColorProperty = getOntologyRepository().getPropertyByIRI(TEST_IRI + "#favoriteColor", PUBLIC);
        assertEquals("Favorite Color", favColorProperty.getDisplayName());
        possibleValues = favColorProperty.getPossibleValues();
        assertEquals(2, possibleValues.size());
        assertEquals("red 1", possibleValues.get("Red"));
        assertEquals("blue 2", possibleValues.get("Blue"));

        Relationship relationship = getOntologyRepository().getRelationshipByIRI(TEST_IRI + "#personKnowsPerson", PUBLIC);
        assertTrue(relationship.getProperties()
                .stream()
                .anyMatch(p -> p.getIri().equals(firstMetProperty.getIri()))
        );
    }

    private void loadTestOwlFile() throws Exception {
        importTestOntologyFile(TEST_OWL, TEST_IRI);
        validateTestOwlRelationship();
        validateTestOwlConcepts(2);
        validateTestOwlProperties();
        validateTestOwlExtendedDataTables();
    }

    private void loadHierarchyOwlFile() throws Exception {
        importTestOntologyFile(TEST_HIERARCHY_OWL, TEST_HIERARCHY_IRI);
    }

    private void importTestOntologyFile(String owlFileResourcePath, String iri) throws Exception {
        URI owlUri = OntologyRepositoryTestBase.class.getResource(owlFileResourcePath).toURI();
        File testOwl;
        if ("jar".equals(owlUri.getScheme())) {
            Path owlDirectoryPath = Files.createTempDirectory(OntologyRepositoryTestBase.class.getSimpleName());
            Path owlFilePath = owlDirectoryPath.resolve("test.owl");
            Path glyphIconPath = owlDirectoryPath.resolve(GLYPH_ICON_FILE);

            testOwl = owlFilePath.toFile();
            InputStream owlFileStream = OntologyRepositoryTestBase.class.getResourceAsStream(owlFileResourcePath);
            IOUtils.copy(owlFileStream, new FileOutputStream(testOwl));

            InputStream glyphIconStream = OntologyRepositoryTestBase.class.getResourceAsStream(GLYPH_ICON_FILE);
            IOUtils.copy(glyphIconStream, new FileOutputStream(glyphIconPath.toFile()));
        } else {
            testOwl = new File(owlUri);
        }
        getOntologyRepository().importFile(testOwl, IRI.create(iri), authorizations);
    }

    @Override
    protected abstract OntologyRepository getOntologyRepository();
}
