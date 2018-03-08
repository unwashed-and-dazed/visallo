package org.visallo.core.model.termMention;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.vertexium.*;
import org.vertexium.inmemory.InMemoryGraph;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.user.GraphAuthorizationRepository;
import org.visallo.core.util.VisalloInMemoryTestBase;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(MockitoJUnitRunner.class)
public class TermMentionRepositoryTest extends VisalloInMemoryTestBase {
    private static final String WORKSPACE_ID = "WORKSPACE_1234";
    private Visibility visibility;
    private Visibility termMentionVisibility;
    private Authorizations authorizations;
    private TermMentionRepository termMentionRepository;

    @Before
    public void setUp() {
        visibility = new Visibility("");
        termMentionVisibility = new Visibility(TermMentionRepository.VISIBILITY_STRING);
        authorizations = getGraph().createAuthorizations(TermMentionRepository.VISIBILITY_STRING);

        termMentionRepository = getTermMentionRepository();
    }

    @Test
    public void testDelete() {
        Vertex v1 = getGraph().addVertex("v1", visibility, authorizations);
        Vertex v1tm1 = getGraph().addVertex("v1tm1", termMentionVisibility, authorizations);
        VisalloProperties.TERM_MENTION_RESOLVED_EDGE_ID.setProperty(v1tm1, "v1_to_v2", termMentionVisibility, authorizations);
        Vertex v2 = getGraph().addVertex("v2", visibility, authorizations);
        getGraph().addEdge("v1_to_c1tm1", v1, v1tm1, VisalloProperties.TERM_MENTION_LABEL_HAS_TERM_MENTION, termMentionVisibility, authorizations);
        getGraph().addEdge("c1tm1_to_v2", v1tm1, v2, VisalloProperties.TERM_MENTION_LABEL_RESOLVED_TO, termMentionVisibility, authorizations);
        Edge e = getGraph().addEdge("v1_to_v2", v1, v2, "link", visibility, authorizations);
        VisibilityJson visibilityJson = new VisibilityJson();
        visibilityJson.addWorkspace(WORKSPACE_ID);
        VisalloProperties.VISIBILITY_JSON.setProperty(e, visibilityJson, new Visibility(""), authorizations);
        getGraph().flush();

        termMentionRepository.delete(v1tm1, authorizations);

        assertNull("term mention should not exist", getGraph().getVertex("v1tm1", authorizations));
        assertNull("term mention to v2 should not exist", getGraph().getEdge("c1tm1_to_v2", authorizations));
        assertNull("v1 to term mention should not exist", getGraph().getEdge("v1_to_c1tm1", authorizations));
    }

    @Test
    public void testFindResolvedToForRef() {
        Vertex v = getGraph().addVertex("v", visibility, authorizations);
        VertexBuilder tmBuilder = getGraph().prepareVertex("tm", termMentionVisibility);
        VisalloProperties.TERM_MENTION_REF_PROPERTY_KEY.setProperty(tmBuilder, "key", termMentionVisibility);
        VisalloProperties.TERM_MENTION_REF_PROPERTY_NAME.setProperty(tmBuilder, "name", termMentionVisibility);
        Vertex tm = tmBuilder.save(authorizations);
        getGraph().addEdge("tm_to_v", tm, v, VisalloProperties.TERM_MENTION_LABEL_RESOLVED_TO, termMentionVisibility, authorizations);
        getGraph().flush();

        List<Vertex> results = termMentionRepository.findResolvedToForRef(v.getId(), "key", "name", authorizations).collect(Collectors.toList());
        assertEquals(1, results.size());
        assertEquals("tm", results.get(0).getId());
    }

    @Test
    public void findResolvedToForRefElement() {
        Vertex v = getGraph().addVertex("v", visibility, authorizations);
        VertexBuilder tmBuilder = getGraph().prepareVertex("tm", termMentionVisibility);
        Vertex tm = tmBuilder.save(authorizations);
        getGraph().addEdge("tm_to_v", tm, v, VisalloProperties.TERM_MENTION_LABEL_RESOLVED_TO, termMentionVisibility, authorizations);
        getGraph().flush();

        List<Vertex> results = termMentionRepository.findResolvedToForRefElement(v.getId(), authorizations).collect(Collectors.toList());
        assertEquals(1, results.size());
        assertEquals("tm", results.get(0).getId());
    }
}