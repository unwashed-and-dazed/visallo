package org.visallo.core.model.search;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.vertexium.*;
import org.vertexium.query.EmptyResultsGraphQuery;
import org.vertexium.query.Query;
import org.visallo.core.config.Configuration;
import org.visallo.core.model.directory.DirectoryRepository;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.model.ontology.Relationship;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class VertexiumObjectSearchRunnerWithRelatedBase extends VertexiumObjectSearchRunnerBase {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(VertexiumObjectSearchRunnerWithRelatedBase.class);

    protected VertexiumObjectSearchRunnerWithRelatedBase(
            OntologyRepository ontologyRepository,
            Graph graph,
            Configuration configuration,
            DirectoryRepository directoryRepository
    ) {
        super(ontologyRepository, graph, configuration, directoryRepository);
    }

    @Override
    protected QueryAndData getQuery(SearchOptions searchOptions, Authorizations authorizations) {
        JSONArray filterJson = getFilterJson(searchOptions, searchOptions.getWorkspaceId());

        String queryStringParam = searchOptions.getOptionalParameter("q", String.class);
        String[] relatedToVertexIdsParam = searchOptions.getOptionalParameter("relatedToVertexIds[]", String[].class);
        String elementExtendedDataParam = searchOptions.getOptionalParameter("elementExtendedData", String.class);

        List<String> relatedToVertexIds = Collections.emptyList();
        ElementExtendedData elementExtendedData = null;
        if (relatedToVertexIdsParam == null && elementExtendedDataParam == null) {
            queryStringParam = searchOptions.getRequiredParameter("q", String.class);
        } else if (elementExtendedDataParam != null) {
            elementExtendedData = ElementExtendedData.fromJsonString(elementExtendedDataParam);
        } else {
            relatedToVertexIds = ImmutableList.copyOf(relatedToVertexIdsParam);
        }

        if (StringUtils.isBlank(queryStringParam)) {
            queryStringParam = null;
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "search %s (relatedToVertexIds: %s, elementExtendedData: %s)\n%s",
                    queryStringParam,
                    Joiner.on(",").join(relatedToVertexIds),
                    elementExtendedData,
                    filterJson.toString(2)
            );
        }

        Query graphQuery = getGraph().query(queryStringParam, authorizations);
        if (elementExtendedData != null) {
            graphQuery = graphQuery.hasExtendedData(elementExtendedData.elementType, elementExtendedData.elementId, elementExtendedData.tableName);
        } else if (!relatedToVertexIds.isEmpty()) {
            String[] edgeLabels = getEdgeLabels(searchOptions);
            Set<String> allRelatedIds = relatedToVertexIds.stream()
                    .map(vertexId -> {
                        Vertex vertex = getGraph().getVertex(vertexId, FetchHint.EDGE_REFS, authorizations);
                        checkNotNull(vertex, "Could not find vertex: " + vertexId);
                        return vertex;
                    })
                    .flatMap(vertex -> {
                        Iterable<EdgeInfo> edgeInfos = vertex.getEdgeInfos(Direction.BOTH, edgeLabels, authorizations);
                        return StreamSupport.stream(edgeInfos.spliterator(), false).map(EdgeInfo::getVertexId);
                    })
                    .collect(Collectors.toSet());
            if (allRelatedIds.isEmpty()) {
                graphQuery = new EmptyResultsGraphQuery();
            } else {
                graphQuery = graphQuery.hasId(allRelatedIds);
            }
        }

        return new QueryAndData(graphQuery);
    }

    private String[] getEdgeLabels(SearchOptions searchOptions) {
        Collection<OntologyRepository.ElementTypeFilter> edgeLabelFilters = getEdgeLabelFilters(searchOptions);
        if (edgeLabelFilters == null || edgeLabelFilters.isEmpty()) {
            return null;
        }

        return edgeLabelFilters.stream()
                .flatMap(filter -> {
                    if (filter.includeChildNodes) {
                        return getOntologyRepository().getRelationshipAndAllChildrenByIRI(filter.iri, searchOptions.getWorkspaceId())
                                .stream().map(Relationship::getIRI);
                    }
                    return Stream.of(filter.iri);
                })
                .toArray(String[]::new);
    }

    private static class ElementExtendedData {
        public final ElementType elementType;
        public final String elementId;
        public final String tableName;

        private ElementExtendedData(
                ElementType elementType,
                String elementId,
                String tableName
        ) {
            this.elementType = elementType;
            this.elementId = elementId;
            this.tableName = tableName;
        }

        public static ElementExtendedData fromJsonString(String str) {
            JSONObject json = new JSONObject(str);
            ElementType elementType = null;
            String elementTypeString = json.optString("elementType");
            if (!Strings.isNullOrEmpty(elementTypeString)) {
                elementType = ElementType.valueOf(elementTypeString.toUpperCase());
            }
            String elementId = json.optString("elementId", null);
            String tableName = json.optString("tableName", null);
            return new ElementExtendedData(elementType, elementId, tableName);
        }

        @Override
        public String toString() {
            return "ElementExtendedData{" +
                    "elementType='" + elementType + '\'' +
                    ", elementId='" + elementId + '\'' +
                    ", tableName='" + tableName + '\'' +
                    '}';
        }
    }
}
