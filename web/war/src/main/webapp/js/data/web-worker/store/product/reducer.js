define(['updeep'], function(u) {
    'use strict';

    return function product(state, { type, payload }) {
        if (!state) return { workspaces: {}, types: [], interacting: {} };

        switch (type) {
            case 'PRODUCT_LIST': return updateList(state, payload);
            case 'PRODUCT_UPDATE_TYPES': return updateTypes(state, payload);
            case 'PRODUCT_UPDATE_TITLE': return updateTitle(state, payload);
            case 'PRODUCT_SELECT': return selectProduct(state, payload);
            case 'PRODUCT_UPDATE': return updateProduct(state, payload);
            case 'PRODUCT_PREVIEW_UPDATE': return updatePreview(state, payload);
            case 'PRODUCT_REMOVE': return removeProduct(state, payload);
            case 'PRODUCT_UPDATE_VIEWPORT': return updateViewport(state, payload);
            case 'PRODUCT_UPDATE_DATA': return updateData(state, payload);
            case 'PRODUCT_UPDATE_EXTENDED_DATA': return updateExtendedData(state, payload);

            case 'PRODUCT_SET_INTERACTING': return setInteracting(state, payload);

            case 'ELEMENT_UPDATE': return updateUnauthorizedElements(state, payload);
        }

        return state;
    };

    function updateTitle(state, { workspaceId, productId, title, loading }) {
        var update;
        if (loading) {
            update = { loading: true }
        } else if (title) {
            update = { loading: false, title }
        }
        if (update) {
            return u({
                workspaces: {
                    [workspaceId]: {
                        products: {
                            [productId]: update
                        }
                    }
                }
            }, state);
        }
        return state;
    }

    function updateViewport(state, { workspaceId, viewport, productId }) {
        return u({
            workspaces: {
                [workspaceId]: {
                    viewports: {
                        [productId]: u.constant(viewport)
                    }
                }
            }
        }, state);
    }

    function updateTypes(state, { types }) {
        return u({ types: u.constant(types) }, state);
    }

    function updatePreview(state, { workspaceId, productId, md5 }) {
        return u({
            workspaces: {
                [workspaceId]: {
                    previewHashes: {
                        [productId]: md5
                    }
                }
            }
        }, state);
    }

    function extractPreviewsAndActive(products = []) {
        return (products || []).reduce((result, product) => {
            const { products, previewHashes } = result;
            const { previewMD5, active, ...rest } = product;

            products[product.id] = rest;
            previewHashes[product.id] = previewMD5;
            if (active && !result.selected) {
                result.selected = product.id
            }

            return result;
        }, { products: {}, previewHashes: {} });
    }

    function updateList(state, { loading, loaded, workspaceId, products }) {
        const { products: productsNoHash, previewHashes, selected } = extractPreviewsAndActive(products);
        const extra = {};
        if (selected) {
            extra.selected = selected;
        }
        return u({
            workspaces: {
                [workspaceId]: {
                    products: u.constant(productsNoHash),
                    previewHashes: u.constant(previewHashes),
                    loading,
                    loaded,
                    ...extra
                }
            }
        }, state);
    }

    function selectProduct(state, { productId, workspaceId }) {
        return u({
            workspaces: {
                [workspaceId]: {
                    selected: productId ? productId : null
                }
            }
        }, state);
    }

    function updateProduct(state, { product }) {
        const { previewMD5, active, ...productNoPreviewOrActive } = product;
        const { id, workspaceId } = productNoPreviewOrActive;
        const existing = state.workspaces[workspaceId].products[id];
        const localData = existing && existing.localData || {};
        const extra = {};
        product.localData = localData;

        if (active) {
            extra.selected = id;
        }

        return u({
            workspaces: {
                [workspaceId]: {
                    products: {
                        [id]: u.constant(productNoPreviewOrActive)
                    },
                    previewHashes: {
                        [id]: previewMD5
                    },
                    ...extra
                }
            }
        }, state);
    }

    function removeProduct(state, { productId, workspaceId }) {
        return u({
            workspaces: {
                [workspaceId]: {
                    products: u.omit(productId),
                    previewHashes: u.omit(productId)
                }
            }
        }, state);
    }

    function updateData(state, {workspaceId, productId, key, value}) {
        return u({
            workspaces: {
                [workspaceId]: {
                    products: {
                        [productId]: {
                            data: {
                                [key]: u.constant(value)
                            }
                        }
                    }
                }
            }
        }, state);
    }

    function updateExtendedData(state, {workspaceId, productId, key, value}) {
        return u({
            workspaces: {
                [workspaceId]: {
                    products: {
                        [productId]: {
                            extendedData: {
                                [key]: u.constant(value)
                            }
                        }
                    }
                }
            }
        }, state);
    }

    function updateLocalData(state, {workspaceId, productId, localData}) {
        return u({
            workspaces: {
                [workspaceId]: {
                    products: {
                        [productId]: {
                            localData: u.constant(localData)
                        }
                    }
                }
            }
        }, state);
    }

    function setInteracting(state, { interactingIds }) {
        const updates = u({
            interacting: interactingIds
        }, state);

        return u({
            interacting: u.omitBy(val => !val)
        }, updates)
    }

    function updateUnauthorizedElements(state, {workspaceId, vertices, edges}) {
        const updateProduct = (product) => {
            if (product.extendedData) {
                const { vertices: productVertices, edges: productEdges } = product.extendedData;
                const transformElements = (productElements, idKey, updateElements) => {
                    return _.mapObject(productElements, (element) => {
                        const { [idKey]: id, unauthorized, ...rest } = element;
                        const update = updateElements.find((e) => e.id === id);
                        if (update !== undefined) {
                            if (update._DELETED !== true) {
                                return { [idKey]: id, ...rest }
                            } else {
                                return { [idKey]: id, unauthorized: true, ...rest };
                            }
                        } else {
                            return element;
                        }
                    });
                };
                const updates = { ...product.extendedData };

                if (productVertices && vertices && vertices.some(({ id }) => productVertices[id])) {
                    updates.vertices = transformElements(productVertices, 'id', vertices);
                }
                if (productEdges && edges && edges.some(({ id }) => productEdges[id])) {
                    updates.edges = transformElements(productEdges, 'edgeId', edges);
                }

                return u({ extendedData: u.constant(updates) }, product)
            } else {
                return product;
            }
        }

        return u({
            workspaces: {
                [workspaceId]: {
                    products: u.map(updateProduct)
                }
            }
        }, state);
    }
});
