package org.visallo.tools;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.inject.Inject;
import org.vertexium.ElementType;
import org.vertexium.Graph;
import org.vertexium.GraphWithSearchIndex;
import org.vertexium.Range;
import org.vertexium.accumulo.AccumuloGraph;
import org.visallo.core.cmdline.CommandLineTool;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.graph.ProxyGraph;
import org.visallo.core.model.longRunningProcess.LongRunningProcessRepository;
import org.visallo.core.model.longRunningProcess.ReindexLongRunningProcessQueueItem;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Parameters(commandDescription = "Reindex elements by enqueueing long running process items to reindex")
public class Reindex extends CommandLineTool {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(Reindex.class);
    private LongRunningProcessRepository longRunningProcessRepository;

    @Parameter(names = {"--vertices", "-v"}, description = "Include all vertices")
    private boolean vertices = false;

    @Parameter(names = {"--edges", "-e"}, description = "Include all edges")
    private boolean edges = false;

    @Parameter(names = {"--all", "-a"}, description = "Include all elements")
    private boolean all = false;

    @Parameter(names = {"--batchSize"}, description = "Number of elements to submit to search index at a time")
    private Integer batchSize = null;

    public static void main(String[] args) throws Exception {
        CommandLineTool.main(new Reindex(), args);
    }

    @Override
    protected int run() throws Exception {
        if (!(getGraph() instanceof GraphWithSearchIndex)) {
            System.err.println("Graph must extend " + GraphWithSearchIndex.class.getName() + " to support reindexing");
            return -1;
        }

        if (!vertices && !edges && !all) {
            System.err.println("You must specify something to index (--vertices, --edges, or --all)");
            return -1;
        }

        if (vertices || all) {
            enqueueVerticesForReindex(batchSize);
        }

        if (edges || all) {
            enqueueEdgesForReindex(batchSize);
        }

        return 0;
    }

    private void enqueueVerticesForReindex(Integer batchSize) {
        enqueueElementsForReindex(ElementType.VERTEX, batchSize);
    }

    private void enqueueEdgesForReindex(Integer batchSize) {
        enqueueElementsForReindex(ElementType.EDGE, batchSize);
    }

    private void enqueueElementsForReindex(ElementType elementType, Integer batchSize) {
        List<String> splits = getSplits(elementType);
        if (splits.size() <= 1) {
            ReindexLongRunningProcessQueueItem reindexQueueItem = new ReindexLongRunningProcessQueueItem(
                    elementType,
                    batchSize,
                    null,
                    null
            );
            longRunningProcessRepository.enqueue(reindexQueueItem, getUser(), getAuthorizations());
            return;
        }

        String lastSplit = null;
        for (String split : splits) {
            ReindexLongRunningProcessQueueItem reindexQueueItem = new ReindexLongRunningProcessQueueItem(
                    elementType,
                    batchSize,
                    lastSplit,
                    split
            );
            longRunningProcessRepository.enqueue(reindexQueueItem, getUser(), getAuthorizations());
            lastSplit = split;
        }
        ReindexLongRunningProcessQueueItem reindexQueueItem = new ReindexLongRunningProcessQueueItem(
                elementType,
                batchSize,
                lastSplit,
                null
        );
        longRunningProcessRepository.enqueue(reindexQueueItem, getUser(), getAuthorizations());
    }

    private List<String> getSplits(ElementType elementType) {
        List<String> splits;

        try {
            splits = getSplitsFromAccumuloGraph(elementType);
            if (splits != null) {
                return splits;
            }
        } catch (NoClassDefFoundError ex) {
            // This can be ignored, this can only happen if AccumuloGraph is not being used, not found on class path
        }

        splits = new ArrayList<>();
        for (char c = ' '; c < '~'; c++) {
            splits.add(Character.toString(c));
        }
        return splits;
    }

    private List<String> getSplitsFromAccumuloGraph(ElementType elementType) {
        Graph graph = getGraph();
        while (graph instanceof ProxyGraph) {
            graph = ((ProxyGraph) graph).getProxiedGraph();
        }

        if (!(graph instanceof AccumuloGraph)) {
            return null;
        }
        AccumuloGraph accumuloGraph = (AccumuloGraph) graph;
        Iterable<Range> splits;
        switch (elementType) {
            case VERTEX:
                splits = accumuloGraph.listVerticesTableSplits();
                break;
            case EDGE:
                splits = accumuloGraph.listEdgesTableSplits();
                break;
            default:
                throw new VisalloException("Unhandled element type: " + elementType);
        }

        List<String> result = new ArrayList<>();
        boolean first = true;
        for (Range split : splits) {
            if (!first) {
                result.add(split.getInclusiveStart());
            }
            first = false;
        }
        return result;
    }

    @Inject
    public void setLongRunningProcessRepository(LongRunningProcessRepository longRunningProcessRepository) {
        this.longRunningProcessRepository = longRunningProcessRepository;
    }
}
