package org.visallo.web.product.graph;

import com.google.common.collect.Queues;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.vertexium.*;
import org.vertexium.util.CloseableUtils;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.graph.ElementUpdateContext;
import org.visallo.core.model.graph.GraphRepository;
import org.visallo.core.model.graph.GraphUpdateContext;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.model.workspace.WorkspaceProperties;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.model.workspace.product.*;
import org.visallo.core.security.VisalloVisibility;
import org.visallo.core.user.User;
import org.visallo.core.util.StreamUtil;
import org.visallo.web.clientapi.model.GraphPosition;
import org.visallo.web.clientapi.model.VisibilityJson;
import org.visallo.web.product.graph.model.GraphUpdateProductEdgeOptions;
import org.visallo.web.product.graph.model.GraphWorkProductExtendedData;
import org.visallo.web.product.graph.model.GraphWorkProductVertex;

import java.util.*;
import java.util.stream.Collectors;

import static org.visallo.web.product.graph.GraphProductOntology.ENTITY_POSITION;

@Singleton
public class GraphWorkProductService extends WorkProductServiceHasElementsBase<GraphWorkProductVertex, WorkProductEdge> {
    public static final String KIND = "org.visallo.web.product.graph.GraphWorkProduct";
    private static final String ROOT_NODE_ID = "root";
    private final AuthorizationRepository authorizationRepository;
    private final GraphRepository graphRepository;
    private final UserRepository userRepository;
    public static final VisalloVisibility VISIBILITY = new VisalloVisibility(WorkspaceRepository.VISIBILITY_STRING);

    @Inject
    public GraphWorkProductService(
            AuthorizationRepository authorizationRepository,
            GraphRepository graphRepository,
            UserRepository userRepository
    ) {
        super(authorizationRepository);
        this.authorizationRepository = authorizationRepository;
        this.graphRepository = graphRepository;
        this.userRepository = userRepository;
    }

    @Override
    public WorkProductExtendedData getExtendedData(
            Graph graph,
            Vertex workspaceVertex,
            Vertex productVertex,
            GetExtendedDataParams params,
            User user,
            Authorizations authorizations
    ) {
        GraphWorkProductExtendedData extendedData = new GraphWorkProductExtendedData();

        if (params.isIncludeVertices()) {
            Nodes nodes = getNodes(graph, productVertex, authorizations);

            extendedData.setVertices(nodes.vertices);
            extendedData.setCompoundNodes(nodes.compoundNodes);
        }

        if (params.isIncludeEdges()) {
            extendedData.setEdges(getEdges(graph, productVertex, user, authorizations));
        }

        return extendedData;
    }

    private static class Nodes {
        public Map<String, GraphWorkProductVertex> vertices;
        public Map<String, GraphWorkProductVertex> compoundNodes;
    }

    private Nodes getNodes(
            Graph graph,
            Vertex productVertex,
            Authorizations authorizations
    ) {
        Map<String, GraphWorkProductVertex> vertices = new HashMap<>();
        Map<String, GraphWorkProductVertex> compoundNodes = new HashMap<>();

        Iterable<Edge> productVertexEdges = productVertex.getEdges(
                Direction.OUT,
                WorkspaceProperties.PRODUCT_TO_ENTITY_RELATIONSHIP_IRI,
                authorizations
        );
        List<String> ids = StreamUtil.stream(productVertexEdges)
                .map(edge -> edge.getOtherVertexId(productVertex.getId()))
                .collect(Collectors.toList());
        Map<String, Boolean> othersById = graph.doVerticesExist(ids, authorizations);

        Iterator<Edge> edgeIterator = productVertexEdges.iterator();
        try {
            while (edgeIterator.hasNext()) {
                Edge propertyVertexEdge = edgeIterator.next();
                String otherId = propertyVertexEdge.getOtherVertexId(productVertex.getId());
                GraphWorkProductVertex vertexOrNode = new GraphWorkProductVertex();
                vertexOrNode.setId(otherId);
                if (!othersById.getOrDefault(otherId, false)) {
                    vertexOrNode.setUnauthorized(true);
                }
                populateProductVertexWithWorkspaceEdge(propertyVertexEdge, vertexOrNode);
                if ("vertex".equals(vertexOrNode.getType())) {
                    vertices.put(otherId, vertexOrNode);
                } else {
                    compoundNodes.put(otherId, vertexOrNode);
                }
            }
        } finally {
            CloseableUtils.closeQuietly(edgeIterator);
        }

        if (compoundNodes.size() > 0) {
            compoundNodes.keySet()
                    .forEach(compoundNodeId -> {
                        GraphWorkProductVertex compoundNode = compoundNodes.get(compoundNodeId);
                        ArrayDeque<GraphWorkProductVertex> childrenDFS = Queues.newArrayDeque();

                        childrenDFS.push(compoundNode);
                        boolean visible = compoundNode.isVisible();
                        while (!visible && !childrenDFS.isEmpty()) {
                            GraphWorkProductVertex next = childrenDFS.poll();
                            List<String> children = next.getChildren();

                            if (children != null) {
                                children.forEach(nextChildId -> {
                                    GraphWorkProductVertex nextChild = vertices.get(nextChildId);
                                    if (nextChild == null) {
                                        nextChild = compoundNodes.get(nextChildId);
                                    }
                                    if (nextChild != null) {
                                        childrenDFS.push(nextChild);
                                    }
                                });
                            } else {
                                visible = !next.isUnauthorized();
                            }
                        }

                        compoundNode.setVisible(visible);
                    });
        }

        Nodes nodes = new Nodes();
        nodes.vertices = vertices;
        nodes.compoundNodes = compoundNodes;
        return nodes;
    }

    private Map<String, WorkProductEdge> getEdges(
            Graph graph,
            Vertex productVertex,
            User user,
            Authorizations authorizations
    ) {
        Map<String, WorkProductEdge> edges = new HashMap<>();
        Authorizations systemAuthorizations = authorizationRepository.getGraphAuthorizations(
                user,
                VisalloVisibility.SUPER_USER_VISIBILITY_STRING
        );
        Iterable<String> productVertexIds = productVertex.getVertexIds(
                Direction.OUT,
                WorkspaceProperties.PRODUCT_TO_ENTITY_RELATIONSHIP_IRI,
                systemAuthorizations
        );
        Iterable<RelatedEdge> productRelatedEdges = graph.findRelatedEdgeSummary(productVertexIds, authorizations);
        List<String> ids = StreamUtil.stream(productRelatedEdges)
                .map(RelatedEdge::getEdgeId)
                .collect(Collectors.toList());
        Map<String, Boolean> relatedEdgesById = graph.doEdgesExist(ids, authorizations);

        for (RelatedEdge relatedEdge : productRelatedEdges) {
            String edgeId = relatedEdge.getEdgeId();
            WorkProductEdge edge = new WorkProductEdge();
            edge.setEdgeId(relatedEdge.getEdgeId());

            if (relatedEdgesById.get(edgeId)) {
                edge.setLabel(relatedEdge.getLabel());
                edge.setOutVertexId(relatedEdge.getOutVertexId());
                edge.setInVertexId(relatedEdge.getInVertexId());
            } else {
                edge.setUnauthorized(true);
            }
            edges.put(edgeId, edge);
        }

        return edges;
    }

    @Override
    public void cleanUpElements(
            Graph graph,
            Vertex productVertex,
            Authorizations authorizations
    ) {
        Iterable<Edge> productElementEdges = productVertex.getEdges(
                Direction.OUT,
                WorkspaceProperties.PRODUCT_TO_ENTITY_RELATIONSHIP_IRI,
                authorizations
        );

        for (Edge productToElement : productElementEdges) {
            if (GraphProductOntology.NODE_CHILDREN.hasProperty(productToElement)) {
                String otherElementId = productToElement.getOtherVertexId(productVertex.getId());
                graph.softDeleteVertex(otherElementId, authorizations);
            } else {
                graph.softDeleteEdge(productToElement, authorizations);
            }
        }

        graph.flush();
    }


    public WorkProductVertex addCompoundNode(
            GraphUpdateContext ctx,
            Vertex productVertex,
            GraphUpdateProductEdgeOptions params,
            User user,
            Visibility visibility,
            Authorizations authorizations
    ) {
        try {
            VisibilityJson visibilityJson = VisibilityJson.updateVisibilitySource(null, "");

            String vertexId = params.getId();
            GraphUpdateContext.UpdateFuture<Vertex> vertexFuture = ctx.getOrCreateVertexAndUpdate(vertexId, null, visibility, elemCtx -> {
                elemCtx.setConceptType(GraphProductOntology.CONCEPT_TYPE_COMPOUND_NODE);
                elemCtx.updateBuiltInProperties(new Date(), visibilityJson);
            });
            vertexId = vertexFuture.get().getId();

            Edge edge = addOrUpdateProductEdgeToEntity(ctx, productVertex, vertexId, params, visibility).get();

            ctx.flush();

            List<String> childIds = params.getChildren();
            for (String childId : childIds) {
                updateParent(ctx, productVertex, childId, vertexId, visibility, authorizations);
            }

            return populateProductVertexWithWorkspaceEdge(ctx.getGraph().getEdge(edge.getId(), authorizations));
        } catch (Exception ex) {
            throw new VisalloException("Could not add compound node", ex);
        }
    }

    public void updateVertices(
            GraphUpdateContext ctx,
            Vertex productVertex,
            Map<String, GraphUpdateProductEdgeOptions> updateVertices,
            User user,
            Visibility visibility,
            Authorizations authorizations
    ) {
        @SuppressWarnings("unchecked")
        Set<String> vertexIds = updateVertices.keySet();
        for (String id : vertexIds) {
            GraphUpdateProductEdgeOptions updateData = updateVertices.get(id);
            String edgeId = getEdgeId(productVertex.getId(), id);

            //undoing compound node removal
            if (updateData.hasChildren() && !ctx.getGraph().doesVertexExist(id, authorizations)) {
                addCompoundNode(ctx, productVertex, updateData, user, visibility, authorizations);
            }

            addOrUpdateProductEdgeToEntity(ctx, productVertex, id, updateData, visibility);
        }
    }

    public void removeVertices(
            GraphUpdateContext ctx,
            Vertex productVertex,
            String[] removeVertices,
            boolean removeChildren,
            User user,
            Visibility visibility,
            Authorizations authorizations
    ) {
        for (String id : removeVertices) {
            String edgeId = getEdgeId(productVertex.getId(), id);
            Edge productVertexEdge = ctx.getGraph().getEdge(edgeId, authorizations);

            if (productVertexEdge != null) {
                String parentId = GraphProductOntology.PARENT_NODE.getPropertyValue(productVertexEdge, ROOT_NODE_ID);
                List<String> children = GraphProductOntology.NODE_CHILDREN.getPropertyValue(productVertexEdge);

                if (children != null && children.size() > 0) {
                    if (removeChildren) {
                        Queue<String> childIdQueue = Queues.newArrayDeque();
                        childIdQueue.addAll(children);

                        while (!childIdQueue.isEmpty()) {
                            String childId = childIdQueue.poll();
                            String childEdgeId = getEdgeId(productVertex.getId(), childId);

                            Edge childEdge = ctx.getGraph().getEdge(childEdgeId, authorizations);
                            List<String> nextChildren = GraphProductOntology.NODE_CHILDREN.getPropertyValue(childEdge);

                            if (nextChildren != null) {
                                childIdQueue.addAll(nextChildren);
                                ctx.getGraph().softDeleteVertex(childId, authorizations);
                            } else {
                                ctx.getGraph().softDeleteEdge(childEdgeId, authorizations);
                            }
                        }
                    } else {
                        children.forEach(childId -> updateParent(ctx, productVertex, childId, parentId, visibility, authorizations));
                        ctx.getGraph().softDeleteVertex(id, authorizations);
                    }
                } else {
                    ctx.getGraph().softDeleteEdge(edgeId, authorizations);
                }

                if (!ROOT_NODE_ID.equals(parentId)) {
                    removeChild(ctx, productVertex, id, parentId, visibility, authorizations);
                }
            }
        }
    }

    private void addChild(
            GraphUpdateContext ctx,
            Vertex productVertex,
            String childId,
            String parentId,
            Visibility visibility,
            Authorizations authorizations
    ) {
        if (parentId.equals(ROOT_NODE_ID)) {
            return;
        }

        String parentEdgeId = getEdgeId(productVertex.getId(), parentId);
        Edge parentProductVertexEdge = ctx.getGraph().getEdge(parentEdgeId, authorizations);

        List<String> children = GraphProductOntology.NODE_CHILDREN.getPropertyValue(parentProductVertexEdge, new ArrayList<>());
        if (!children.contains(childId)) {
            children.add(childId);

            GraphUpdateProductEdgeOptions parentOptions = new GraphUpdateProductEdgeOptions();
            parentOptions.getChildren().addAll(children);
            addOrUpdateProductEdgeToEntity(ctx, parentEdgeId, productVertex, childId, parentOptions, visibility);

            GraphUpdateProductEdgeOptions childOptions = new GraphUpdateProductEdgeOptions();
            childOptions.setParent(parentId);
            addOrUpdateProductEdgeToEntity(ctx, productVertex, childId, childOptions, visibility);
        }
    }

    private void removeChild(
            GraphUpdateContext ctx,
            Vertex productVertex,
            String childId,
            String parentId,
            Visibility visibility,
            Authorizations authorizations
    ) {
        if (parentId.equals(ROOT_NODE_ID)) {
            return;
        }

        String edgeId = getEdgeId(productVertex.getId(), parentId);
        Edge productVertexEdge = ctx.getGraph().getEdge(edgeId, authorizations);
        List<String> children = GraphProductOntology.NODE_CHILDREN.getPropertyValue(productVertexEdge);

        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i).equals(childId)) {
                    children.remove(i);
                    break;
                }
            }
            if (children.size() == 0) {
                ctx.getGraph().softDeleteVertex(parentId, authorizations);

                String ancestorId = GraphProductOntology.PARENT_NODE.getPropertyValue(productVertexEdge, ROOT_NODE_ID);
                if (ancestorId.equals(productVertex.getId())) {
                    removeChild(ctx, productVertex, parentId, ancestorId, visibility, authorizations);
                }
            } else {
                EdgeBuilderByVertexId edgeBuilder = ctx.getGraph().prepareEdge(
                        edgeId,
                        productVertex.getId(),
                        parentId,
                        WorkspaceProperties.PRODUCT_TO_ENTITY_RELATIONSHIP_IRI,
                        visibility
                );
                edgeBuilder.setProperty(GraphProductOntology.NODE_CHILDREN.getPropertyName(), children, visibility);

                edgeBuilder.save(authorizations);
                ctx.flush();
            }
        }
    }

    private void updateParent(
            GraphUpdateContext ctx,
            Vertex productVertex,
            String childId,
            String parentId,
            Visibility visibility,
            Authorizations authorizations
    ) {
        String edgeId = getEdgeId(productVertex.getId(), childId);
        Edge productVertexEdge = ctx.getGraph().getEdge(edgeId, authorizations);
        GraphUpdateProductEdgeOptions updateData = new GraphUpdateProductEdgeOptions();
        GraphPosition graphPosition;

        String oldParentId = GraphProductOntology.PARENT_NODE.getPropertyValue(productVertexEdge, ROOT_NODE_ID);
        graphPosition = calculatePositionFromParents(ctx, productVertex, childId, oldParentId, parentId, authorizations);

        updateData.setPos(graphPosition);
        updateData.setParent(parentId);

        addOrUpdateProductEdgeToEntity(ctx, productVertex, childId, updateData, visibility);

        removeChild(ctx, productVertex, childId, oldParentId, visibility, authorizations);
        addChild(ctx, productVertex, childId, parentId, visibility, authorizations);
    }

    private GraphPosition calculatePositionFromParents(
            GraphUpdateContext ctx,
            Vertex productVertex,
            String childId,
            String oldParentId,
            String newParentId,
            Authorizations authorizations
    ) {
        boolean newParentIsDescendant = false;
        if (!newParentId.equals(ROOT_NODE_ID)) {
            Edge newParentEdge = ctx.getGraph().getEdge(getEdgeId(productVertex.getId(), newParentId), authorizations);
            String parentNode = GraphProductOntology.PARENT_NODE.getPropertyValue(newParentEdge, ROOT_NODE_ID);
            newParentIsDescendant = parentNode.equals(oldParentId);
        }

        GraphPosition parentOffset;
        String parentOffsetId = newParentIsDescendant ? newParentId : oldParentId;
        if (parentOffsetId.equals(ROOT_NODE_ID)) {
            parentOffset = new GraphPosition(0, 0);
        } else {
            String offsetEdgeId = getEdgeId(productVertex.getId(), parentOffsetId);
            Edge offsetEdge = ctx.getGraph().getEdge(offsetEdgeId, authorizations);
            parentOffset = getGraphPosition(offsetEdge);
        }

        String childEdgeId = getEdgeId(productVertex.getId(), childId);
        Edge childEdge = ctx.getGraph().getEdge(childEdgeId, authorizations);

        GraphPosition graphPosition = getGraphPosition(childEdge);

        if (newParentIsDescendant) {
            graphPosition.subtract(parentOffset);
        } else {
            graphPosition.add(parentOffset);
        }

        return graphPosition;
    }

    private GraphPosition getGraphPosition(Edge productVertexEdge) {
        return ENTITY_POSITION.getPropertyValue(productVertexEdge);
    }

    @Override
    protected void updateProductEdge(
            ElementUpdateContext<Edge> elemCtx,
            UpdateProductEdgeOptions updateOptions,
            Visibility visibility
    ) {
        if (updateOptions instanceof GraphUpdateProductEdgeOptions) {
            GraphUpdateProductEdgeOptions update = (GraphUpdateProductEdgeOptions) updateOptions;
            GraphPosition position = update.getPos();
            if (position != null) {
                ENTITY_POSITION.updateProperty(elemCtx, position, visibility);
            }

            String parent = update.getParent();
            if (parent != null) {
                GraphProductOntology.PARENT_NODE.updateProperty(elemCtx, parent, visibility);
            }

            if (update.hasChildren()) {
                GraphProductOntology.NODE_CHILDREN.updateProperty(elemCtx, update.getChildren(), visibility);
            }
        }
    }


    @Override
    protected void populateCustomProductVertexWithWorkspaceEdge(Edge propertyVertexEdge, GraphWorkProductVertex vertex) {
        GraphPosition position = ENTITY_POSITION.getPropertyValue(propertyVertexEdge);
        String parent = GraphProductOntology.PARENT_NODE.getPropertyValue(propertyVertexEdge, ROOT_NODE_ID);
        List<String> children = GraphProductOntology.NODE_CHILDREN.getPropertyValue(propertyVertexEdge);
        String title = GraphProductOntology.NODE_TITLE.getPropertyValue(propertyVertexEdge);

        if (position == null) {
            position = new GraphPosition(0, 0);
        }
        vertex.setPos(position);

        if (children != null) {
            vertex.setChildren(children);
            vertex.setType("compoundNode");
        } else {
            vertex.setType("vertex");
        }

        if (title != null) {
            vertex.setTitle(title);
        }
        vertex.setParent(parent);
    }

    @Override
    public String getKind() {
        return KIND;
    }

    @Override
    protected GraphWorkProductVertex createWorkProductVertex() {
        return new GraphWorkProductVertex();
    }

    @Override
    protected WorkProductEdge createWorkProductEdge() {
        return new WorkProductEdge();
    }
}
