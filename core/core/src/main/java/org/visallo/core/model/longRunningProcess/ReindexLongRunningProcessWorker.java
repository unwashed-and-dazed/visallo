package org.visallo.core.model.longRunningProcess;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.json.JSONObject;
import org.vertexium.*;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.Description;
import org.visallo.core.model.Name;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Name("Reindex")
@Description("Reindexes the specified elements")
@Singleton
public class ReindexLongRunningProcessWorker extends LongRunningProcessWorker {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(ReindexLongRunningProcessWorker.class);
    private static final EnumSet<FetchHint> FETCH_HINTS = FetchHint.ALL;
    private final Authorizations authorizations;
    private final Graph graph;

    @Inject
    public ReindexLongRunningProcessWorker(
            Graph graph,
            UserRepository userRepository,
            AuthorizationRepository authorizationRepository
    ) {
        this.graph = graph;
        this.authorizations = authorizationRepository.getGraphAuthorizations(userRepository.getSystemUser());
    }

    @Override
    public boolean isHandled(JSONObject jsonObject) {
        return ReindexLongRunningProcessQueueItem.isHandled(jsonObject);
    }

    @Override
    protected void processInternal(JSONObject longRunningProcessQueueItem) {
        ReindexLongRunningProcessQueueItem queueItem = ClientApiConverter.toClientApi(
                longRunningProcessQueueItem.toString(),
                ReindexLongRunningProcessQueueItem.class
        );
        int batchSize = queueItem.getBatchSize();
        Range range = new Range(queueItem.getStartId(), queueItem.getEndId());
        LOGGER.info("reindex %s %s", range, queueItem.getElementType());
        if (queueItem.getElementType() == ElementType.VERTEX) {
            reindexVertices(range, batchSize, authorizations);
        } else if (queueItem.getElementType() == ElementType.EDGE) {
            reindexEdges(range, batchSize, authorizations);
        } else {
            throw new VisalloException("Unhandled element type: " + queueItem.getElementType());
        }
    }

    public void reindexVertices(Range range, int batchSize, Authorizations authorizations) {
        Iterable<Vertex> vertices = graph.getVerticesInRange(range, FETCH_HINTS, authorizations);
        reindexElements(vertices, batchSize, authorizations);
    }

    public void reindexEdges(Range range, int batchSize, Authorizations authorizations) {
        Iterable<Edge> edges = graph.getEdgesInRange(range, FETCH_HINTS, authorizations);
        reindexElements(edges, batchSize, authorizations);
    }

    private void reindexElements(
            Iterable<? extends Element> elements,
            int batchSize,
            Authorizations authorizations
    ) {
        List<Element> batch = new ArrayList<>(batchSize);
        for (Element element : elements) {
            batch.add(element);
            if (batch.size() == batchSize) {
                ((GraphWithSearchIndex) graph).getSearchIndex().addElements(graph, batch, authorizations);
                batch.clear();
            }
        }
        if (batch.size() > 0) {
            ((GraphWithSearchIndex) graph).getSearchIndex().addElements(graph, batch, authorizations);
            batch.clear();
        }
    }
}
