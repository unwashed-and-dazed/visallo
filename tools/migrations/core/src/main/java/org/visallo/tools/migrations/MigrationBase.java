package org.visallo.tools.migrations;

import org.vertexium.Graph;
import org.vertexium.GraphFactory;
import org.visallo.core.cmdline.CommandLineTool;
import org.visallo.core.config.Configuration;
import org.visallo.core.exception.VisalloException;

import static org.visallo.core.bootstrap.VisalloBootstrap.GRAPH_METADATA_VISALLO_GRAPH_VERSION_KEY;

public abstract class MigrationBase extends CommandLineTool {
    private Graph graph = null;

    @Override
    public final int run(String[] args, boolean initFramework) throws Exception {
        return super.run(args, initFramework);
    }

    @Override
    protected final int run() throws Exception {
        graph = getGraph();
        try {
            Object visalloGraphVersionObj = graph.getMetadata(GRAPH_METADATA_VISALLO_GRAPH_VERSION_KEY);
            if (visalloGraphVersionObj == null) {
                throw new VisalloException("No graph metadata version set");
            } else if (visalloGraphVersionObj instanceof Integer) {
                Integer visalloGraphVersion = (Integer) visalloGraphVersionObj;
                if (getFinalGraphVersion().equals(visalloGraphVersion)) {
                    throw new VisalloException("Migration has already completed. Graph version: " + visalloGraphVersion);
                } else if (!getNeededGraphVersion().equals(visalloGraphVersion)) {
                    throw new VisalloException("Migration can only run from version " + getNeededGraphVersion() +
                            ". Current graph version = " + visalloGraphVersion);
                }
            } else {
                throw new VisalloException("Unexpected value for graph version: " + visalloGraphVersionObj);
            }

            if (migrate(graph)) {
                graph.setMetadata(GRAPH_METADATA_VISALLO_GRAPH_VERSION_KEY, getFinalGraphVersion());
            }

            graph.flush();
            afterMigrate(graph);

            return 0;
        } finally {
            graph.shutdown();
        }
    }

    public abstract Integer getNeededGraphVersion();

    public abstract Integer getFinalGraphVersion();

    protected abstract boolean migrate(Graph graph);

    protected void afterMigrate(Graph graph) {
    }

    @Override
    public Graph getGraph() {
        if (graph == null) {
            GraphFactory factory = new GraphFactory();
            graph = factory.createGraph(getConfiguration().getSubset(Configuration.GRAPH_PROVIDER));
        }
        return graph;
    }
}
