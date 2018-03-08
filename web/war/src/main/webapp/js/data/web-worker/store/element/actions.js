define(['../actions'], function(actions) {
    actions.protectFromWorker();

    return actions.createActions({
        workerImpl: 'data/web-worker/store/element/actions-impl',
        actions: {
            get: ({ workspaceId, vertexIds, edgeIds }) => ({ workspaceId, vertexIds, edgeIds }),
            setFocus: ({ vertexIds = [], edgeIds = [], elementIds = [] }) => ({ vertexIds, edgeIds, elementIds }),
            updateElement: (workspaceId, vertex) => ({ workspaceId, vertex }),
            refreshElement: ({ workspaceId, vertexId, edgeId }) => ({ workspaceId, vertexId, edgeId })
        }
    })
})

