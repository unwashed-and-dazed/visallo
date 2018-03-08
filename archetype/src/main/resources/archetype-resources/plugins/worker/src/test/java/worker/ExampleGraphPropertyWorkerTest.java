#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.worker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.vertexium.*;
import org.vertexium.id.QueueIdGenerator;
import org.vertexium.inmemory.InMemoryAuthorizations;
import org.vertexium.property.StreamingPropertyValue;
import org.vertexium.query.Query;
import org.vertexium.query.SortDirection;
import org.visallo.core.ingest.graphProperty.GraphPropertyWorkerTestBase;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.workspace.Workspace;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static ${package}.worker.OntologyConstants.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.visallo.core.util.StreamUtil.stream;

@RunWith(MockitoJUnitRunner.class)
public class ExampleGraphPropertyWorkerTest extends GraphPropertyWorkerTestBase {
    private static final String VISIBILITY_SOURCE = "TheVisibilitySource";
    private static final String WORKSPACE_ID = "WORKSPACE_ID";
    private Visibility visibility;
    private Authorizations authorizations;
    private Vertex archiveVertex;
    private ExampleGraphPropertyWorker worker;

    @Before
    public void before() throws Exception {
        for (int i = 0; i < 100; i++) {
            ((QueueIdGenerator) getGraphIdGenerator()).push("id" + i);
        }

        VisibilityJson visibilityJson = new VisibilityJson();
        visibilityJson.addWorkspace(WORKSPACE_ID);
        visibility = getVisibilityTranslator().toVisibility(visibilityJson).getVisibility();
        authorizations = new InMemoryAuthorizations(VISIBILITY_SOURCE, WORKSPACE_ID);

        archiveVertex = getGraph().addVertex(visibility, authorizations);
        VisalloProperties.MIME_TYPE.addPropertyValue(archiveVertex, "", "text/csv", visibility, authorizations);
        VisalloProperties.VISIBILITY_JSON.setProperty(archiveVertex, visibilityJson, visibility, authorizations);

        InputStream archiveIn = getClass().getResource("/contacts.csv").openStream();
        StreamingPropertyValue value = new StreamingPropertyValue(archiveIn, byte[].class);
        VisalloProperties.RAW.setProperty(archiveVertex, value, visibility, authorizations);
        archiveIn.close();

        Workspace workspace = mock(Workspace.class);
        when(workspace.getWorkspaceId()).thenReturn(WORKSPACE_ID);
        when(workspaceRepository.findById(WORKSPACE_ID, getUser())).thenReturn(workspace);
        when(workspaceRepository.toClientApi(any(), any(), any())).thenCallRealMethod();

        worker = new ExampleGraphPropertyWorker();
    }

    @Test
    public void isHandledReturnsTrueForRawPropertyWithCsvMimeType() throws Exception {
        boolean handled = worker.isHandled(archiveVertex, VisalloProperties.RAW.getProperty(archiveVertex));

        assertThat(handled, is(true));
    }

    @Test
    public void isHandledReturnsFalseForRawPropertyWithOtherMimeType() throws Exception {
        VisalloProperties.MIME_TYPE.removeProperty(archiveVertex, "", authorizations);
        VisalloProperties.MIME_TYPE.addPropertyValue(
                archiveVertex, "", "application/octet-stream", visibility, authorizations);

        boolean handled = worker.isHandled(archiveVertex, VisalloProperties.RAW.getProperty(archiveVertex));

        assertThat(handled, is(false));
    }

    @Test
    public void isHandledReturnsFalseForNullProperty() throws Exception {
        assertThat(worker.isHandled(archiveVertex, null), is(false));
    }

    @Test
    public void executeShouldCreatePersonVerticesFromContactsCsvFileVertex() throws Exception {
        run(worker, getWorkerPrepareData(), archiveVertex, WORKSPACE_ID);

        Query csvFileQuery = getGraph().query(authorizations)
                .has(VisalloProperties.CONCEPT_TYPE.getPropertyName(), CONTACTS_CSV_FILE_CONCEPT_TYPE);
        List<Vertex> csvFileVertices = stream(csvFileQuery.vertices()).collect(Collectors.toList());
        assertThat(csvFileVertices.size(), is(1));
        Vertex csvFileVertex = csvFileVertices.get(0);

        Query personQuery = getGraph().query(authorizations)
                .has(VisalloProperties.CONCEPT_TYPE.getPropertyName(), PERSON_CONCEPT_TYPE)
                .sort(PERSON_FULL_NAME_PROPERTY.getPropertyName(), SortDirection.ASCENDING);
        List<Vertex> personVertices = stream(personQuery.vertices()).collect(Collectors.toList());
        assertThat(personVertices.size(), is(2));

        Vertex person1Vertex = personVertices.get(0);
        assertThat(PERSON_FULL_NAME_PROPERTY.getPropertyValue(person1Vertex), is("Bruce Wayne"));
        assertThat(PERSON_EMAIL_ADDRESS_PROPERTY.getPropertyValue(person1Vertex), is("batman@example.org"));
        assertThat(PERSON_PHONE_NUMBER_PROPERTY.getPropertyValue(person1Vertex), is("888-555-0102"));
        assertThat(person1Vertex.getVisibility().hasAuthorization(WORKSPACE_ID), is(true));
        List<EdgeInfo> person1Edges = stream(
                person1Vertex.getEdgeInfos(Direction.IN, HAS_ENTITY_EDGE_LABEL, authorizations))
                .collect(Collectors.toList());
        assertThat(person1Edges.size(), is(1));
        assertThat(person1Edges.get(0).getVertexId(), is(csvFileVertex.getId()));

        Vertex person2Vertex = personVertices.get(1);
        assertThat(PERSON_FULL_NAME_PROPERTY.getPropertyValue(person2Vertex), is("Clark Kent"));
        assertThat(PERSON_EMAIL_ADDRESS_PROPERTY.getPropertyValue(person2Vertex), is("superman@example.org"));
        assertThat(PERSON_PHONE_NUMBER_PROPERTY.getPropertyValue(person2Vertex), is("888-555-0101"));
        assertThat(person2Vertex.getVisibility().hasAuthorization(WORKSPACE_ID), is(true));
        List<EdgeInfo> person2Edges = stream(
                person2Vertex.getEdgeInfos(Direction.IN, HAS_ENTITY_EDGE_LABEL, authorizations))
                .collect(Collectors.toList());
        assertThat(person2Edges.size(), is(1));
        assertThat(person2Edges.get(0).getVertexId(), is(csvFileVertex.getId()));
    }
}
