/* global publicData:0 */
define([
    'data/web-worker/store/element/selectors',
    'data/web-worker/store/product/selectors'
], function(elementSelectors, productSelectors) {

    //check new edges for product inclusion (both vertices are in the product)
    publicData.storePromise.then(store => store.observe(elementSelectors.getEdges, (newEdges, prevEdges) => {
        const state = store.getState();
        const workspaceId = state.workspace.currentId;

        productSelectors.getProducts(state).forEach(product => {
            if (product.extendedData) {
                const { vertices, edges } = product.extendedData;
                const addEdges = {};

                _.each(newEdges, (edge, id) => {
                    if (edge !== null && !(id in edges)) {
                        if (edge.inVertexId in vertices && edge.outVertexId in vertices) {
                            addEdges[id] = {
                                edgeId: id,
                                ..._.pick(edge, 'inVertexId', 'outVertexId', 'label')
                            };
                        }
                    }
                })

                if (Object.keys(addEdges).length) {
                    _.defer(() => {
                        store.dispatch({
                            type: 'PRODUCT_ADD_EDGE_IDS',
                            payload: { workspaceId, productId: product.id, edges: addEdges }
                        })
                    })
                }
            }
        });
    }));
});
