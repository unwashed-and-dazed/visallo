define([
    'public/v1/api',
    'openlayers',
    '../util/layerHelpers',
    'util/mapConfig',
    'util/vertex/formatters'
], function(
    api,
    ol,
    layerHelpers,
    mapConfig,
    F) {
    'use strict';

    const featureCache = {};

    return api.defineComponent(GeoShapePreview);

    function GeoShapePreview() {

        this.attributes({
            ignoreUpdateModelNotImplemented: true
        })

        this.before('initialize', function(node, config) {
            this.element = config.model;

            this.unsubscribePadding = visalloData.storePromise.then(store => {
                const paddingSelector = (state) => state.panel.padding;
                store.observe(paddingSelector, (nextPadding, prevPadding) => {
                    if (nextPadding && (!prevPadding || nextPadding.right !== prevPadding.right)) {
                        this.onDetailPaneResize();
                    }
                });
            })
        });

        this.after('initialize', function() {
            this.setupMap();
        })

        this.before('teardown', function() {
            if (_.isFunction(this.unsubscribePadding)) {
                this.unsubscribePadding();
            }
        });

        this.setupMap = function() {
            const { vectorXhr: layerHelper, tile } = layerHelpers.byType;
            const { source, sourceOptions } = mapConfig();

            const { layer: tileLayer } = tile.configure('base', { source, sourceOptions });
            const { source: olSource, layer: geoShapeLayer } = layerHelper.configure(this.element.id, {
                id: this.element.id,
                element: this.element,
                propName: 'http://visallo.org#raw',
                propKey: '',
                mimeType: F.vertex.prop(this.element, 'http://visallo.org#mimeType'),
                sourceOptions: {
                    wrapX: false
                }
            });

            const map = new ol.Map({
                target: this.node,
                layers: [
                    tileLayer,
                    geoShapeLayer
                ],
                controls: [new ol.control.Zoom()],
                view: new ol.View({
                    zoom: 2,
                    minZoom: 1,
                    center: [0, 0],
                })
            });

            this.geoShapeLayer = geoShapeLayer;
            this.map = map;

            let featurePromise = featureCache[this.element.id] || layerHelper.loadFeatures(olSource, geoShapeLayer);

            Promise.resolve(featurePromise).then((features) => {
                const view = this.map.getView();
                const olSource = this.geoShapeLayer.getSource();

                olSource.addFeatures(features);
                this.geoShapeLayer.set('status', 'loaded');

                view.fit(olSource.getExtent());

                if (!featureCache[this.element.id]) {
                    featureCache[this.element.id] = features;
                }
            });
        };

        this.onDetailPaneResize = function() {
            if (this.map) {
                this.map.updateSize();
            }
        }
    }
});
