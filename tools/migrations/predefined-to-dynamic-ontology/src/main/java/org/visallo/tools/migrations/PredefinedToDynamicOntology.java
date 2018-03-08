package org.visallo.tools.migrations;

import com.beust.jcommander.Parameters;
import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.vertexium.GraphWithSearchIndex;
import org.vertexium.TextIndexHint;
import org.vertexium.search.SearchIndex;
import org.visallo.core.bootstrap.VisalloBootstrap;
import org.visallo.core.cmdline.CommandLineTool;
import org.visallo.core.model.ontology.OntologyProperties;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.security.VisalloVisibility;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.vertexium.model.ontology.VertexiumOntologyRepository;

@Parameters(commandDescription = "Migrate from a strictly predefined ontology to a dynamic ontology")
public class PredefinedToDynamicOntology extends MigrationBase {
    private static VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(PredefinedToDynamicOntology.class);

    public static void main(String[] args) throws Exception {
        CommandLineTool.main(new PredefinedToDynamicOntology(), args, false);
    }

    @Override
    public Integer getNeededGraphVersion() {
        return 3;
    }

    @Override
    public Integer getFinalGraphVersion() {
        return 4;
    }

    @Override
    protected boolean migrate(Graph graph) {
        LOGGER.info("Updating Vertexium property definitions.");
        graph.defineProperty(OntologyProperties.DISPLAY_NAME.getPropertyName())
                .dataType(String.class)
                .textIndexHint(TextIndexHint.EXACT_MATCH)
                .define();
        graph.defineProperty(OntologyProperties.INTENT.getPropertyName())
                .dataType(String.class)
                .textIndexHint(TextIndexHint.EXACT_MATCH)
                .define();
        graph.defineProperty(OntologyProperties.USER_VISIBLE.getPropertyName())
                .dataType(Boolean.TYPE)
                .define();
        graph.defineProperty(OntologyProperties.SEARCHABLE.getPropertyName())
                .dataType(Boolean.TYPE)
                .define();
        graph.defineProperty(OntologyProperties.SORTABLE.getPropertyName())
                .dataType(Boolean.TYPE)
                .define();
        graph.defineProperty(OntologyProperties.ADDABLE.getPropertyName())
                .dataType(Boolean.TYPE)
                .define();

        return true;
    }

    @Override
    protected void afterMigrate(Graph graph) {
        if (graph instanceof GraphWithSearchIndex) {
            LOGGER.info("Re-indexing ontology elements...");

            VisalloBootstrap bootstrap = VisalloBootstrap.bootstrap(getConfiguration());
            SearchIndex searchIndex = ((GraphWithSearchIndex) graph).getSearchIndex();
            Authorizations authorizations = graph.createAuthorizations(VisalloVisibility.SUPER_USER_VISIBILITY_STRING, OntologyRepository.VISIBILITY_STRING);
            graph.getVerticesWithPrefix(VertexiumOntologyRepository.ID_PREFIX, authorizations).forEach(vertex -> {
                LOGGER.debug("Adding vertex %s to search index.", vertex.getId());
                searchIndex.addElement(graph, vertex, authorizations);
            });
            searchIndex.flush(graph);
        }
    }
}
