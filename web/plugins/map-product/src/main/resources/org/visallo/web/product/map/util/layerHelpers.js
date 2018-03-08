define([
    'openlayers',
    '../multiPointCluster',
    'util/withDataRequest',
    './cache',
], function(
    ol,
    MultiPointCluster,
    withDataRequest,
    cache
) {
    'use strict';

    const FEATURE_CLUSTER_RADIUS = 12;
    const FEATURE_CLUSTER_RADIUS_MAX = 20;
    const VECTOR_FEATURE_SELECTION_OVERLAY = 'org-visallo-map-vector-selected-overlay';

    const DEFAULT_LAYER_CONFIG = {
        sortable: true,
        toggleable: true
    };

    const layers = {
        tile: {
            configure(id, options = {}) {
                const { source, sourceOptions = {}, ...config } = options;
                let baseLayerSource;

                if (source in ol.source && _.isFunction(ol.source[source])) {
                    baseLayerSource = new ol.source[source]({
                        crossOrigin: 'anonymous',
                        ...sourceOptions
                    });
                } else {
                    console.error('Unknown map provider type: ', source);
                    throw new Error('map.provider is invalid')
                }

                const layer = new ol.layer.Tile({
                    ...DEFAULT_LAYER_CONFIG,
                    id,
                    label: 'Base',
                    type: 'tile',
                    sortable: false,
                    source: baseLayerSource,
                    ...config
                });

                return { source: baseLayerSource, layer }
            },

            addEvents(map, { source }, handlers) {
                return [
                    source.on('tileloaderror', function(event) {
                        const MaxRetry = 3;
                        const { tile } = event;

                        if (tile) {
                            tile._retryCount = (tile._retryCount || 0) + 1;
                            if (tile._retryCount <= MaxRetry) {
                                console.warn(`Tile error retry: ${tile._retryCount} of ${MaxRetry}`, tile.src_);
                                _.defer(() => {
                                    tile.load();
                                })
                            }
                        }
                    })
                ]
            }
        },

        cluster: {
            configure(id, options = {}) {
                const source = new ol.source.Vector({ features: [] });
                const clusterSource = new MultiPointCluster({ source });
                const layer = new ol.layer.Vector({
                    ...DEFAULT_LAYER_CONFIG,
                    id,
                    label: 'Cluster',
                    type: 'cluster',
                    style: cluster => this.style(cluster, { source }),
                    source: clusterSource,
                    ...options
                });
                const heatmap = new ol.layer.Heatmap({
                    ...DEFAULT_LAYER_CONFIG,
                    ...options,
                    visible: false,
                    id: 'heatmap_cluster',
                    label: 'Heatmap',
                    type: 'cluster_heatmap',
                    source
                })

                cache.clear();

                return { source, clusterSource, layers: [heatmap, layer] }
            },

            style(cluster, { source, selected = false } = {}) {
                const count = cluster.get('count');
                const selectionState = cluster.get('selectionState') || 'none';
                const isSelected = selected || selectionState !== 'none';

                if (count > 1) {
                    return styles.cluster(cluster, { selected: isSelected, source });
                } else {
                    return styles.feature(cluster.get('features')[0], { selected: isSelected })
                }
            },

            addEvents(map, { source, clusterSource, layers }, handlers) {
                const [ heatmapLayer, vectorLayer ] = layers;
                const addToElements = list => feature => {
                    const el = feature.get('element');
                    const key = el.type === 'vertex' ? 'vertices' : 'edges';
                    list[key].push(el.id);
                };
                const isPartiallySelected = (cluster) => {
                    if (cluster.get('count') < 2) return false;

                    const features = cluster.get('features');
                    const selected = features.filter(f => f.get('selected'));
                    return 0 < selected.length && selected.length < features.length;
                };
                const getClusterFromEvent = ({ pixel }) => {
                    const pixelFeatures = map.getFeaturesAtPixel(pixel, {
                        layerFilter: layer => layer === vectorLayer
                    });
                    return pixelFeatures && pixelFeatures[0];
                };

                // For heatmap selections
                const onHeatmapClick = map.on('click', ({ pixel }) => {
                    const elements = { vertices: [], edges: [] };
                    map.forEachFeatureAtPixel(pixel, addToElements(elements), {
                        layerFilter: layer => layer === heatmapLayer
                    });

                    if (elements.vertices.length || elements.edges.length) {
                        handlers.onSelectElements(elements);
                    }
                });

                // For partial cluster selections
                const onClusterClick = map.on('click', event => {
                    const targetFeature = getClusterFromEvent(event);

                    if (targetFeature && isPartiallySelected(targetFeature)) {
                        const elements = { vertices: [], edges: [] };
                        const clusterIterator = addToElements(elements);

                        targetFeature.get('features').forEach(clusterIterator);
                        handlers.onAddSelection(elements);
                    }
                });

                //this does not support ol.interaction.Select.multi because of partial cluster selection
                const selectInteraction = new ol.interaction.Select({
                    addCondition: (event) => {
                        if (event.originalEvent.shiftKey) {
                            return true;
                        } else {
                            const targetFeature = getClusterFromEvent(event);
                            return !!targetFeature && isPartiallySelected(targetFeature);
                        }
                    },
                    condition: ol.events.condition.click,
                    toggleCondition: ol.events.condition.platformModifierKeyOnly,
                    layers: [vectorLayer],
                    style: cluster => this.style(cluster, { source, selected: true })
                });

                map.addInteraction(selectInteraction);

                const onSelectCluster = selectInteraction.on('select', function(event) {
                    const { selected, target: interaction } = event;
                    const clusters = interaction.getFeatures();
                    const elements = { vertices: [], edges: [] };
                    const clusterIterator = addToElements(elements);

                    clusters.forEach(cluster => {
                        let features = cluster.get('features');
                        if (isPartiallySelected(cluster) && !selected.includes(cluster)) {
                            features = features.filter(f => f.get('selected'));
                        }
                        features.forEach(clusterIterator);
                    });

                    handlers.onSelectElements(elements);
                });

                const onClusterSourceChange = clusterSource.on('change', _.debounce(function() {
                    var selected = selectInteraction.getFeatures(),
                        clusters = this.getFeatures(),
                        newSelection = [],
                        isSelected = feature => feature.get('selected');

                    clusters.forEach(cluster => {
                        var innerFeatures = cluster.get('features');
                        var all = true, some = false, count = 0;
                        innerFeatures.forEach(feature => {
                            const selected = isSelected(feature);
                            all = all && selected;
                            some = some || selected;
                            count += (selected ? 1 : 0)
                        })

                        if (some) {
                            newSelection.push(cluster);
                            cluster.set('selectionState', all ? 'all' : 'some');
                            cluster.set('selectionCount', count);
                        } else {
                            cluster.unset('selectionState');
                            cluster.unset('selectionCount');
                        }
                    })

                    selected.clear()
                    if (newSelection.length) {
                        selected.extend(newSelection)
                    }
                }, 100));

                return [
                    onHeatmapClick,
                    onClusterClick,
                    onSelectCluster,
                    onClusterSourceChange
                ]
            },

            update: syncFeatures
        },

        ancillary: {
            configure(id, options = {}, map) {
                const source = new ol.source.Vector({
                    features: [],
                    wrapX: false
                });
                if (options.getExtent) {
                    const _superExtent = source.getExtent;
                    source.getExtent = function() {
                        const extent = _superExtent && _superExtent.apply(this, arguments);
                        const customExtent = options.getExtent(map, source, extent);
                        if (ol.extent.isEmpty(customExtent)) {
                            return extent || ol.extent.createEmpty();
                        }
                        return customExtent || extent || ol.extent.createEmpty();
                    };
                }
                const layer = new ol.layer.Vector({
                    ...DEFAULT_LAYER_CONFIG,
                    id,
                    type: 'ancillary',
                    sortable: false,
                    toggleable: false,
                    source,
                    renderBuffer: 500,
                    updateWhileInteracting: true,
                    updateWhileAnimating: true,
                    style: ancillary => this.style(ancillary),
                    ...options
                });

                return { source, layer }
            },

            style(ancillary) {
                const extensionStyles = ancillary.get('styles');
                if (extensionStyles) {
                    const { normal } = extensionStyles;
                    if (normal.length) {
                        return normal;
                    }
                }
            },

            update: syncFeatures
        },

        vectorXhr: {
            configure(id, options = {}) {
                const { sourceOptions = {}, ...layerOptions } = options;
                const source = new ol.source.Vector(sourceOptions);
                const layer = new ol.layer.Vector({
                    ...DEFAULT_LAYER_CONFIG,
                    id,
                    type: 'vectorXhr',
                    source,
                    ...layerOptions
                });

                return { source, layer };
            },

            addEvents(map, { source: olSource, layer }, handlers) {
                const elements = { vertices: [], edges: [] };
                const element = layer.get('element');
                const key = element.type === 'vertex' ? 'vertices' : 'edges';
                const overlayId = getOverlayIdForLayer(layer);

                elements[key].push(element.id);

                const onGeoShapeClick = map.on('click', (e) => {
                    const { map, pixel } = e;
                    const featuresAtPixel = map.getFeaturesAtPixel(pixel);
                    const sourceFeatures = olSource.getFeatures();

                    if (featuresAtPixel) {
                        if (featuresAtPixel.length === 1
                            && featuresAtPixel[0].getId() === overlayId
                            && olSource.getFeatureById(overlayId)) {
                            handlers.onSelectElements({ vertices: [], edges: [] });
                        } else if (featuresAtPixel.every(feature => sourceFeatures.includes(feature))) {
                            handlers.onSelectElements(elements);
                        }
                    }
                });

                const onLayerFeaturesLoaded = olSource.on('propertyChange', (e) => {
                    if (e.key === 'status' && e.target.get(e.key) === 'loaded') {
                        const selectionOverlay = olSource.getFeatureById(overlayId);

                        if (selectionOverlay) {
                            let extent;

                            olSource.forEachFeature(feature => {
                                const geom = feature.getGeometry();
                                const featureExtent = geom.getExtent();

                                if (feature.getId() !== overlayId) {
                                    if (extent) {
                                        ol.extent.extend(extent, featureExtent);
                                    } else {
                                        extent = featureExtent;
                                    }
                                }
                            });

                            const geometry = ol.geom.Polygon.fromExtent(extent);
                            selectionOverlay.setGeometry(geometry);
                        }
                    }
                });

                return [ onGeoShapeClick, onLayerFeaturesLoaded ]
            },

            update(source, { source: olSource, layer }) {
                const { element, features, selected } = source;
                const layerStatus = layer.get('status');
                const nextFeatures = [];
                let changed = false;
                let fitFeatures;

                if (element !== layer.get('element')) {
                    olSource.set('element', element);
                    changed = true;
                }

                if (!layerStatus) {
                    this.loadFeatures(olSource, layer).then((features) => {
                        if (features) {
                            olSource.clear(true)
                            olSource.addFeatures(features);
                            layer.set('status', 'loaded');
                        }
                    });
                } else if (selected !== olSource.get('selected')) {
                    const overlayId = getOverlayIdForLayer(layer);
                    olSource.set('selected', selected);
                    changed = true;

                    if (selected && layerStatus === 'loaded') {
                        let extent;
                        olSource.forEachFeature(feature => {
                            const geom = feature.getGeometry();
                            const featureExtent = geom.getExtent();

                            if (feature.getId() !== overlayId) {
                                if (extent) {
                                    ol.extent.extend(extent, featureExtent);
                                } else {
                                    extent = featureExtent;
                                }
                            }
                        });

                        const selectedOverlay = new ol.Feature(ol.geom.Polygon.fromExtent(extent || [0, 0, 0, 0]));
                        selectedOverlay.setStyle(new ol.style.Style({
                            fill: new ol.style.Fill({ color: [0, 136, 204, 0.3] }),
                            stroke: new ol.style.Stroke({ color: [0, 136, 204, 0.4], width: 1 })
                        }));
                        selectedOverlay.setId(overlayId)

                        olSource.addFeature(selectedOverlay);
                    } else {
                        const selectedOverlay = olSource.getFeatureById(overlayId);
                        if (selectedOverlay) {
                            olSource.removeFeature(selectedOverlay);
                        }
                    }
                }

                return { changed };
            },

            loadFeatures(olSource, layer) {
                const { id, element, propName, propKey, mimeType } = layer.getProperties();

                layer.set('status', 'loading');

                return withDataRequest.dataRequest('vertex', 'propertyValue', id, propName, propKey).then(source => {
                    const format = getFormatForMimeType(mimeType);
                    const dataProjection = format.readProjection(source);

                    if (!dataProjection || !ol.proj.get(dataProjection.getCode())) {
                        throw new Error('unhandledDataProjection');
                    } else {
                        const features = format.readFeatures(source, {
                            dataProjection,
                            featureProjection: 'EPSG:3857'
                        });

                        return features;
                    }

                })
                    .then(features => {
                        return features.map((feature, i) => {
                            feature.setId(`${layer.get('id')}:${i}`)
                            feature.set('element', element)

                            return feature
                        })
                    })
                    .catch(e => {
                        const message = e.message === 'unhandledDataProjection'
                            ? i18n('org.visallo.web.product.map.MapWorkProduct.layer.error.data.format')
                            : i18n('org.visallo.web.product.map.MapWorkProduct.layer.error');

                        layer.set('status', { type: 'error', message });
                    });
            }
        }
    };


    const styles = {
        feature(feature, { selected = false } = {}) {
            const {
                focused,
                focusedDim,
                styles: extensionStyles,
                selected: featureSelected,
                _nodeRadius: radius
            } = feature.getProperties();

            const isSelected = selected || featureSelected;
            let needSelectedStyle = true;
            let needFocusStyle = true;
            let styleList;

            if (extensionStyles) {
                const { normal: normalStyle, selected: selectedStyle } = extensionStyles;
                let style;
                if (normalStyle.length && (!isSelected || !selectedStyle.length)) {
                    style = normalStyle;
                } else if (selectedStyle.length && isSelected) {
                    style = selectedStyle;
                }

                if (style) {
                    styleList = _.isArray(style) ? style : [style];
                }
            } else {
                needSelectedStyle = false;
                needFocusStyle = false;
                styleList = cache.getOrCreateFeature({
                    src: feature.get(isSelected ? 'iconUrlSelected' : 'iconUrl'),
                    imgSize: feature.get('iconSize'),
                    scale: 1 / feature.get('pixelRatio'),
                    anchor: feature.get('iconAnchor')
                }, focused)
            }

            if (_.isEmpty(styleList)) {
                console.warn('No styles for feature, ignoring.', feature);
                return [];
            }

            if (needFocusStyle && focused) {
                return cache.addFocus(radius, cache.reset(radius, styleList));
            }
            if (focusedDim) {
                return cache.addDim(radius, styleList)
            }

            return cache.reset(radius, styleList);
        },

        cluster(cluster, { selected = false, source, clusterSource } = {}) {
            var count = cluster.get('count'),
                focusStats = cluster.get('focusStats'),
                selectionState = cluster.get('selectionState') || 'none',
                selectionCount = cluster.get('selectionCount') || 0,
                { min, max } = source.countStats,
                value = Math.min(max, Math.max(min, count)),
                radius = min === max ?
                    FEATURE_CLUSTER_RADIUS :
                    interpolate(value, min, max, FEATURE_CLUSTER_RADIUS, FEATURE_CLUSTER_RADIUS_MAX);

            return cache.getOrCreateCluster({
                count, radius, selected, selectionState, selectionCount, focusStats
            })
        }
    };

    function interpolate(v, x0, x1, y0, y1) {
        return (y0 * (x1 - v) + y1 * (v - x0)) / (x1 - x0)
    }

    function setLayerConfig(config = {}, layer) {
        const { visible = true, opacity = 1, zIndex = 0, ...properties } = config;

        _.mapObject(properties, (value, key) => {
            if (value === null) {
                layer.unset(key);
            } else {
                layer.set(key, value);
            }
        })

        layer.setVisible(visible)
        layer.setOpacity(opacity)
        layer.setZIndex(zIndex)
    }

    function syncFeatures({ features }, { source }, focused) {
        const existingFeatures = _.indexBy(source.getFeatures(), f => f.getId());
        const newFeatures = [];
        var changed = false;

        if (features) {
            for (let featureIndex = 0; featureIndex < features.length; featureIndex++) {
                const data = features[featureIndex];
                const { id, styles, geometry: geometryOverride, geoLocations, element, ...rest } = data;
                let geometry = null;

                if (geometryOverride) {
                    geometry = geometryOverride;
                } else if (geoLocations) {
                    geometry = cache.getOrCreateGeometry(id, geoLocations);
                }

                if (geometry) {
                    let featureValues = {
                        ...rest,
                        element,
                        geoLocations,
                        geometry
                    };

                    if (styles) {
                        const { normal, selected } = styles;
                        if (normal && normal.length) {
                            const radius = getRadiusFromStyles(normal);
                            const normalImage = _.isFunction(normal[0].getImage) &&
                                normal[0].getImage();

                            featureValues._nodeRadius = radius

                            if (selected.length === 0 && !geometryOverride && normalImage && _.isFunction(normalImage.getStroke)) {
                                const newSelected = normal[0].clone();
                                const prevStroke = normal[0].getImage().getStroke();
                                const newStroke = new ol.style.Stroke({
                                    color: '#0088cc',
                                    width:  prevStroke && prevStroke.getWidth() || 1
                                })

                                newSelected.image_ = normal[0].getImage().clone({
                                    stroke: newStroke,
                                    opacity: 1
                                });

                                featureValues.styles = {
                                    normal,
                                    selected: [newSelected]
                                }
                            } else {
                                featureValues.styles = styles;
                            }
                        }
                    }

                    if (focused && focused.isFocusing) {
                        if (element.id in focused[element.type === 'vertex' ? 'vertices' : 'edges']) {
                            featureValues.focused = true
                            featureValues.focusedDim = false
                        } else {
                            featureValues.focused = false
                            featureValues.focusedDim = true
                        }
                    } else {
                        featureValues.focused = false
                        featureValues.focusedDim = false
                    }

                    if (id in existingFeatures) {
                        const existingFeature = existingFeatures[id];
                        let diff = _.any(existingFeature.getProperties(), (val, name) => {
                            switch (name) {
                                case 'styles':
                                case 'interacting':
                                    return false
                                case 'geoLocations':
                                    return !_.isEqual(val, featureValues[name])
                                default:
                                    return val !== featureValues[name]
                            }
                        })

                        if (diff) {
                            changed = true
                            if (existingFeature.get('interacting')) {
                                delete featureValues.geometry;
                            }
                            existingFeature.setProperties(featureValues)
                        }
                        delete existingFeatures[id];
                    } else {
                        var feature = new ol.Feature(featureValues);
                        feature.setId(data.id);
                        newFeatures.push(feature);
                    }
                }
            }
        }

        let fitFeatures;
        if (newFeatures.length) {
            changed = true
            source.addFeatures(newFeatures);
            fitFeatures = newFeatures;
        }
        if (!_.isEmpty(existingFeatures)) {
            changed = true
            _.forEach(existingFeatures, feature => source.removeFeature(feature));
        }
        return { changed, fitFeatures };
    }

    function getFormatForMimeType(mimeType) {
        switch (mimeType) {
            case 'application/vnd.geo+json':
                return new ol.format.GeoJSON();
            case 'application/vnd.google-earth.kml+xml':
                return new ol.format.KML();
        }
    }

    function getOverlayIdForLayer(layer) {
        return layer.get('id') + '|' + VECTOR_FEATURE_SELECTION_OVERLAY;
    }

    function getRadiusFromStyles(styles) {
        for (let i = styles.length - 1; i >= 0; i--) {
            if (_.isFunction(styles[i].getImage)) {
                const image = styles[i].getImage();
                const radius = image && _.isFunction(image.getRadius) && image.getRadius();

                if (radius) {
                    const nodeRadius = radius / devicePixelRatio
                    return nodeRadius;
                }
            }
        }
    }

    return {
        byType: layers,
        styles,
        setLayerConfig
    }
})
