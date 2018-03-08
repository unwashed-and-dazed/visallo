package org.visallo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.vertexium.*;
import org.vertexium.property.StreamingPropertyValue;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.ingest.graphProperty.GraphPropertyWorkerTestBase;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.mimeTypeOntologyMapper.MimeTypeOntologyMapperGraphPropertyWorker;
import org.visallo.vertexium.model.ontology.InMemoryConcept;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.visallo.core.model.ontology.OntologyRepository.PUBLIC;
import static org.visallo.mimeTypeOntologyMapper.MimeTypeOntologyMapperGraphPropertyWorker.*;

@RunWith(MockitoJUnitRunner.class)
public class MimeTypeOntologyMapperGraphPropertyWorkerTest extends GraphPropertyWorkerTestBase {
    private static final String DEFAULT_CONCEPT_IRI = "http://visallo.org/junit#defaultConcept";
    private static final String MULTIVALUE_KEY = MimeTypeOntologyMapperGraphPropertyWorkerTest.class.getName();
    private static final String TEXT_CONCEPT_IRI = "http://visallo.org/junit#textConcept";

    private static final String TEXT_MIME_TYPE = "text/plain";
    private static final String PNG_MIME_TYPE = "image/png";

    private MimeTypeOntologyMapperGraphPropertyWorker gpw;

    private Map<String, String> extraConfiguration = new HashMap<>();

    @Before
    public void setup() throws Exception {
        gpw = new MimeTypeOntologyMapperGraphPropertyWorker();

        InMemoryConcept defaultConcept = new InMemoryConcept(DEFAULT_CONCEPT_IRI, VisalloProperties.CONCEPT_TYPE_THING, null);
        when(ontologyRepository.getRequiredConceptByIRI(DEFAULT_CONCEPT_IRI, PUBLIC)).thenReturn(defaultConcept);

        String defaultConfigKey = MimeTypeOntologyMapperGraphPropertyWorker.class.getName() + ".mapping." + DEFAULT_MAPPING_KEY + "." + MAPPING_IRI_KEY;
        extraConfiguration.put(defaultConfigKey, DEFAULT_CONCEPT_IRI);
    }

    @Override
    protected Map getConfigurationMap() {
        Map configurationMap = super.getConfigurationMap();
        configurationMap.putAll(extraConfiguration);
        return configurationMap;
    }

    @Test
    public void testUnknownMimeTypeWithNoDefaultConfigured() {
        extraConfiguration.clear();
        Vertex vertex = run(TEXT_MIME_TYPE);
        assertNull("GPW should not have set a concept type", VisalloProperties.CONCEPT_TYPE.getPropertyValue(vertex));
    }

    @Test
    public void testUnknownMimeTypeGetsDefault() {
        Vertex vertex = run(TEXT_MIME_TYPE);
        assertEquals("GPW should have set default concept type", DEFAULT_CONCEPT_IRI, VisalloProperties.CONCEPT_TYPE.getPropertyValue(vertex));
    }

    @Test
    public void testMappingWithNoRegex() {
        String configKey = MimeTypeOntologyMapperGraphPropertyWorker.class.getName() + ".mapping.texFiles." + MAPPING_IRI_KEY;
        extraConfiguration.put(configKey, TEXT_CONCEPT_IRI);
        try {
            run(TEXT_MIME_TYPE);
        } catch (VisalloException ve) {
            assertTrue(ve.getMessage().contains("Failed to prepare"));
            assertTrue(ve.getCause().getMessage().contains("Expected mapping name of default or a regex"));
        }
    }

    @Test
    public void testMappingForText() {
        extraConfiguration.put(MimeTypeOntologyMapperGraphPropertyWorker.class.getName() + ".mapping.texFiles." + MAPPING_INTENT_KEY, "textFile");
        extraConfiguration.put(MimeTypeOntologyMapperGraphPropertyWorker.class.getName() + ".mapping.texFiles." + MAPPING_REGEX_KEY, "text/.+");

        InMemoryConcept textConcept = new InMemoryConcept(TEXT_CONCEPT_IRI, VisalloProperties.CONCEPT_TYPE_THING, null);
        when(ontologyRepository.getRequiredConceptByIntent("textFile", PUBLIC)).thenReturn(textConcept);

        Vertex vertex = run(TEXT_MIME_TYPE);
        assertEquals("GPW should have set text concept type", TEXT_CONCEPT_IRI, VisalloProperties.CONCEPT_TYPE.getPropertyValue(vertex));
    }

    @Test
    public void testMappingForTextWithNonTextVertex() {
        extraConfiguration.put(MimeTypeOntologyMapperGraphPropertyWorker.class.getName() + ".mapping.texFiles." + MAPPING_INTENT_KEY, "textFile");
        extraConfiguration.put(MimeTypeOntologyMapperGraphPropertyWorker.class.getName() + ".mapping.texFiles." + MAPPING_REGEX_KEY, "text/.+");

        InMemoryConcept textConcept = new InMemoryConcept(TEXT_CONCEPT_IRI, VisalloProperties.CONCEPT_TYPE_THING, null);
        when(ontologyRepository.getRequiredConceptByIntent("textFile", PUBLIC)).thenReturn(textConcept);

        Vertex vertex = run(PNG_MIME_TYPE);
        assertEquals("GPW should have set default concept type", DEFAULT_CONCEPT_IRI, VisalloProperties.CONCEPT_TYPE.getPropertyValue(vertex));
    }

    private Vertex run(String mimeType) {
        VisibilityJson visibilityJson = new VisibilityJson("MimeTypeOntologyMapperGraphPropertyWorkerTest");
        Visibility visibility = getVisibilityTranslator().toVisibility(visibilityJson).getVisibility();
        Authorizations authorizations = getGraph().createAuthorizations("MimeTypeOntologyMapperGraphPropertyWorkerTest");

        VertexBuilder vertexBuilder = getGraph().prepareVertex("v1", visibility);

        Metadata textMetadata = new Metadata();
        VisalloProperties.MIME_TYPE_METADATA.setMetadata(textMetadata, mimeType, getVisibilityTranslator().getDefaultVisibility());
        VisalloProperties.VISIBILITY_JSON.setProperty(vertexBuilder, visibilityJson, getVisibilityTranslator().getDefaultVisibility());
        VisalloProperties.MIME_TYPE.addPropertyValue(vertexBuilder, MULTIVALUE_KEY, mimeType, getVisibilityTranslator().getDefaultVisibility());
        StreamingPropertyValue textPropertyValue = new StreamingPropertyValue(new ByteArrayInputStream("hello".getBytes()), String.class);
        VisalloProperties.RAW.setProperty(vertexBuilder, textPropertyValue, textMetadata, visibility);

        Vertex vertex = vertexBuilder.save(authorizations);
        Property property = vertex.getProperty(VisalloProperties.RAW.getPropertyName());
        boolean didRun = run(gpw, getWorkerPrepareData(), vertex, property, null);
        assertTrue("Graph property worker didn't run", didRun);

        return getGraph().getVertex(vertex.getId(), authorizations);
    }
}
