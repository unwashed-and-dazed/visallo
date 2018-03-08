package org.visallo.core.model.search;

import org.vertexium.VertexiumObject;
import org.vertexium.query.QueryResultsIterable;

public class QueryResultsIterableSearchResults extends VertexiumObjectsSearchResults implements AutoCloseable {
    private final QueryResultsIterable<? extends VertexiumObject> searchResults;
    private final VertexiumObjectSearchRunnerBase.QueryAndData queryAndData;
    private final Long offset;
    private final Long size;

    public QueryResultsIterableSearchResults(
            QueryResultsIterable<? extends VertexiumObject> searchResults,
            VertexiumObjectSearchRunnerBase.QueryAndData queryAndData,
            Long offset,
            Long size
    ) {
        this.searchResults = searchResults;
        this.queryAndData = queryAndData;
        this.offset = offset;
        this.size = size;
    }

    public QueryResultsIterable<? extends VertexiumObject> getQueryResultsIterable() {
        return searchResults;
    }

    public VertexiumObjectSearchRunnerBase.QueryAndData getQueryAndData() {
        return queryAndData;
    }

    public Long getOffset() {
        return offset;
    }

    public Long getSize() {
        return size;
    }

    @Override
    public void close() throws Exception {
        this.searchResults.close();
    }

    @Override
    public Iterable<? extends VertexiumObject> getVertexiumObjects() {
        return searchResults;
    }
}
