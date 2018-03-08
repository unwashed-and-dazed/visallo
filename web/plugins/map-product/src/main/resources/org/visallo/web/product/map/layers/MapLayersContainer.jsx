define([
    'react-redux',
    'data/web-worker/store/selection/actions',
    'data/web-worker/store/product/actions',
    'data/web-worker/store/product/selectors',
    'data/web-worker/store/ontology/selectors',
    '../worker/actions',
    './MapLayers'
], function(
    redux,
    selectionActions,
    productActions,
    productSelectors,
    ontologySelectors,
    mapActions,
    MapLayers) {
    'use strict';

    const mimeTypes = [VISALLO_MIMETYPES.ELEMENTS];
    const LAYERS_EXTENDED_DATA_KEY = 'org-visallo-map-layers';

    return redux.connect(
        (state, props) => {
            const { product, map, cluster, layersWithSources, ...injectedProps } = props;
            const editable = state.workspace.byId[state.workspace.currentId].editable;
            const baseLayer = map.getLayers().item(0);
            const layers = map.getLayers().getArray().slice(1).reverse();
            const layerIds = layers.reduce((order, layer) => {
                order.push(layer.get('id'));
                return order;
            }, []);
            const layersExtendedData = product.extendedData && product.extendedData[LAYERS_EXTENDED_DATA_KEY] || {};
            const layerOrder = layersExtendedData.layerOrder || [];
            const layersConfig = layersExtendedData.config || {};

            return {
                ...injectedProps,
                product,
                map,
                baseLayer,
                layersConfig,
                layerOrder,
                layerIds,
                layers,
                editable
            };
        },

        (dispatch, props) => {
            return {
                setLayerOrder: (layerOrder) => dispatch(mapActions.setLayerOrder(props.product.id, layerOrder)),
                updateLayerConfig: (config, layerId) => {
                    const extendedData = props.product.extendedData[LAYERS_EXTENDED_DATA_KEY];
                    const layersConfig = { ...(extendedData.config || {}), [layerId]: config };

                    dispatch(productActions.updateExtendedData(
                        props.product.id,
                        LAYERS_EXTENDED_DATA_KEY,
                        { ...extendedData, config: layersConfig }
                    ));
                }
            };
        }
    )(MapLayers)
});
