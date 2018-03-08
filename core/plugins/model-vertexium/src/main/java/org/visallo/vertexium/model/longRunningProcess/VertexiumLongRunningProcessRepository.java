package org.visallo.vertexium.model.longRunningProcess;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.json.JSONObject;
import org.vertexium.*;
import org.vertexium.util.ConvertingIterable;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.graph.GraphRepository;
import org.visallo.core.model.graph.GraphUpdateContext;
import org.visallo.core.model.longRunningProcess.LongRunningProcessProperties;
import org.visallo.core.model.longRunningProcess.LongRunningProcessRepository;
import org.visallo.core.model.properties.types.PropertyMetadata;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.user.GraphAuthorizationRepository;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.model.workQueue.Priority;
import org.visallo.core.model.workQueue.WorkQueueRepository;
import org.visallo.core.security.VisalloVisibility;
import org.visallo.core.user.SystemUser;
import org.visallo.core.user.User;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.vertexium.util.IterableUtils.toList;

@Singleton
public class VertexiumLongRunningProcessRepository extends LongRunningProcessRepository {
    private final WorkQueueRepository workQueueRepository;
    private final GraphRepository graphRepository;
    private final UserRepository userRepository;
    private final Graph graph;
    private final AuthorizationRepository authorizationRepository;

    @Inject
    public VertexiumLongRunningProcessRepository(
            GraphRepository graphRepository,
            GraphAuthorizationRepository graphAuthorizationRepository,
            UserRepository userRepository,
            WorkQueueRepository workQueueRepository,
            Graph graph,
            AuthorizationRepository authorizationRepository
    ) {
        this.graphRepository = graphRepository;
        this.userRepository = userRepository;
        this.workQueueRepository = workQueueRepository;
        this.graph = graph;
        this.authorizationRepository = authorizationRepository;

        graphAuthorizationRepository.addAuthorizationToGraph(VISIBILITY_STRING);
    }

    @Override
    public String enqueue(JSONObject longRunningProcessQueueItem, User user, Authorizations authorizations) {
        authorizations = getAuthorizations(user);

        Vertex userVertex;
        if (user instanceof SystemUser) {
            userVertex = null;
        } else {
            userVertex = graph.getVertex(user.getUserId(), authorizations);
            checkNotNull(userVertex, "Could not find user with id: " + user.getUserId());
        }
        Visibility visibility = getVisibility();

        String longRunningProcessVertexId;
        try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(Priority.LOW, user, authorizations)) {
            ctx.setPushOnQueue(false);
            longRunningProcessVertexId = ctx.update(this.graph.prepareVertex(visibility), elemCtx -> {
                PropertyMetadata metadata = new PropertyMetadata(user, new VisibilityJson(), visibility);
                elemCtx.updateBuiltInProperties(metadata);
                elemCtx.setConceptType(LongRunningProcessProperties.LONG_RUNNING_PROCESS_CONCEPT_IRI);
                longRunningProcessQueueItem.put("enqueueTime", System.currentTimeMillis());
                longRunningProcessQueueItem.put("userId", user.getUserId());
                LongRunningProcessProperties.QUEUE_ITEM_JSON_PROPERTY.updateProperty(elemCtx, longRunningProcessQueueItem, metadata);
            }).get().getId();

            if (userVertex != null) {
                ctx.getOrCreateEdgeAndUpdate(
                        null,
                        userVertex.getId(),
                        longRunningProcessVertexId,
                        LongRunningProcessProperties.LONG_RUNNING_PROCESS_TO_USER_EDGE_IRI,
                        visibility,
                        elemCtx -> {
                        }
                );
            }
        } catch (Exception ex) {
            throw new VisalloException("Could not create long running process vertex", ex);
        }

        longRunningProcessQueueItem.put("id", longRunningProcessVertexId);
        this.workQueueRepository.pushLongRunningProcessQueue(longRunningProcessQueueItem);

        return longRunningProcessVertexId;
    }

    public Authorizations getAuthorizations(User user) {
        Authorizations authorizations;
        authorizations = authorizationRepository.getGraphAuthorizations(
                user,
                VISIBILITY_STRING,
                UserRepository.VISIBILITY_STRING
        );
        return authorizations;
    }

    @Override
    public void beginWork(JSONObject longRunningProcessQueueItem) {
        super.beginWork(longRunningProcessQueueItem);
        updateVertexWithJson(longRunningProcessQueueItem);
    }

    @Override
    public void ack(JSONObject longRunningProcessQueueItem) {
        updateVertexWithJson(longRunningProcessQueueItem);
    }

    @Override
    public void nak(JSONObject longRunningProcessQueueItem, Throwable ex) {
        updateVertexWithJson(longRunningProcessQueueItem);
    }

    public void updateVertexWithJson(JSONObject longRunningProcessQueueItem) {
        String longRunningProcessGraphVertexId = longRunningProcessQueueItem.getString("id");
        Authorizations authorizations = getAuthorizations(userRepository.getSystemUser());
        try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(Priority.LOW, userRepository.getSystemUser(), authorizations)) {
            ctx.setPushOnQueue(false);
            Vertex vertex = this.graph.getVertex(longRunningProcessGraphVertexId, authorizations);
            checkNotNull(vertex, "Could not find long running process vertex: " + longRunningProcessGraphVertexId);
            ctx.update(vertex, elemCtx -> {
                PropertyMetadata metadata = new PropertyMetadata(userRepository.getSystemUser(), new VisibilityJson(), getVisibility());
                LongRunningProcessProperties.QUEUE_ITEM_JSON_PROPERTY.updateProperty(
                        elemCtx,
                        longRunningProcessQueueItem,
                        metadata
                );
            });
        }
    }

    @Override
    public List<JSONObject> getLongRunningProcesses(User user) {
        Authorizations authorizations = getAuthorizations(user);
        Vertex userVertex = graph.getVertex(user.getUserId(), authorizations);
        checkNotNull(userVertex, "Could not find user with id: " + user.getUserId());
        Iterable<Vertex> longRunningProcessVertices = userVertex.getVertices(
                Direction.OUT,
                LongRunningProcessProperties.LONG_RUNNING_PROCESS_TO_USER_EDGE_IRI,
                authorizations
        );
        return toList(new ConvertingIterable<Vertex, JSONObject>(longRunningProcessVertices) {
            @Override
            protected JSONObject convert(Vertex longRunningProcessVertex) {
                JSONObject json = LongRunningProcessProperties.QUEUE_ITEM_JSON_PROPERTY.getPropertyValue(
                        longRunningProcessVertex);
                json.put("id", longRunningProcessVertex.getId());
                return json;
            }
        });
    }

    @Override
    public JSONObject findById(String longRunningProcessId, User user) {
        Authorizations authorizations = getAuthorizations(user);
        Vertex vertex = this.graph.getVertex(longRunningProcessId, authorizations);
        if (vertex == null) {
            return null;
        }
        return LongRunningProcessProperties.QUEUE_ITEM_JSON_PROPERTY.getPropertyValue(vertex);
    }

    @Override
    public void cancel(String longRunningProcessId, User user) {
        Authorizations authorizations = getAuthorizations(userRepository.getSystemUser());
        Vertex vertex = this.graph.getVertex(longRunningProcessId, authorizations);
        checkNotNull(vertex, "Could not find long running process vertex: " + longRunningProcessId);
        JSONObject json = LongRunningProcessProperties.QUEUE_ITEM_JSON_PROPERTY.getPropertyValue(vertex);
        json.put("canceled", true);
        json.put("id", longRunningProcessId);

        try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(Priority.LOW, user, authorizations)) {
            ctx.setPushOnQueue(false);
            VertexBuilder vb = graph.prepareVertex(longRunningProcessId, vertex.getVisibility());
            ctx.update(vb, elemCtx -> {
                PropertyMetadata metadata = new PropertyMetadata(userRepository.getSystemUser(), new VisibilityJson(), getVisibility());
                LongRunningProcessProperties.QUEUE_ITEM_JSON_PROPERTY.updateProperty(
                        elemCtx,
                        json,
                        metadata
                );
            });
        }

        workQueueRepository.broadcastLongRunningProcessChange(json);
    }

    @Override
    public void reportProgress(String longRunningProcessGraphVertexId, double progressPercent, String message) {
        Authorizations authorizations = getAuthorizations(userRepository.getSystemUser());
        Vertex vertex = this.graph.getVertex(longRunningProcessGraphVertexId, authorizations);
        checkNotNull(vertex, "Could not find long running process vertex: " + longRunningProcessGraphVertexId);

        JSONObject object = LongRunningProcessProperties.QUEUE_ITEM_JSON_PROPERTY.getPropertyValue(vertex);
        if (object.optBoolean("canceled", false)) {
            throw new VisalloException("Unable to update progress of cancelled process");
        }

        JSONObject json = LongRunningProcessProperties.QUEUE_ITEM_JSON_PROPERTY.getPropertyValue(vertex);
        json.put("progress", progressPercent);
        json.put("progressMessage", message);
        json.put("id", longRunningProcessGraphVertexId);

        VertexBuilder vb = graph.prepareVertex(longRunningProcessGraphVertexId, vertex.getVisibility());
        LongRunningProcessProperties.QUEUE_ITEM_JSON_PROPERTY.setProperty(
                vb,
                json,
                getVisibility()
        );
        vb.save(authorizations);
        this.graph.flush();

        workQueueRepository.broadcastLongRunningProcessChange(json);
    }

    @Override
    public void delete(String longRunningProcessId, User authUser) {
        Authorizations authorizations = getAuthorizations(authUser);
        Vertex vertex = this.graph.getVertex(longRunningProcessId, authorizations);
        JSONObject json = null;
        if (vertex != null) {
            json = LongRunningProcessProperties.QUEUE_ITEM_JSON_PROPERTY.getPropertyValue(vertex);
        }
        this.graph.deleteVertex(vertex, authorizations);
        this.graph.flush();

        if (json != null) {
            workQueueRepository.broadcastLongRunningProcessDeleted(json);
        }
    }

    private Visibility getVisibility() {
        return new VisalloVisibility(VISIBILITY_STRING).getVisibility();
    }
}
