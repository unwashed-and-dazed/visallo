define([
    'openlayers',
    'util/mapConfig',
    'colorjs'
], function(ol, mapConfig, colorjs) {

    const Escape = 27;
    const NORMAL = 'normal';
    const HEATMAP = 'heatmap';
    const dataProjection = 'EPSG:4326';
    const featureProjection = 'EPSG:3857';

    return withMapTiles;

    function withMapTiles() {

        this.updateSize = function() {
            this.map.updateSize();
        };

        this.render = function(d3, node, data, d3tip) {
            var $node = $(node);
            if (!data) {
                $node.empty();
                return;
            }

            const { geoJson, predicate, ...layerConfig } = data;

            if (this.map) {
                if ($node.html()) {
                    this.updateSize();
                } else {
                    this.vectorSource.clear();
                    this.map.setTarget(null)
                    _.defer(() => {
                        this.map.setTarget(node)
                        this.viewportListeners();
                        this.vectorSource.addFeatures(this.getFeatures(geoJson));
                        this.fit();
                    })
                }
                return;
            }

            const vectorSource = new ol.source.Vector({ features: this.getFeatures(geoJson) })
            const vectorLayer = this.getLayer(d3, layerConfig, vectorSource);

            const { source, sourceOptions } = mapConfig();
            const map = new ol.Map({
                target: node,
                layers: [
                    new ol.layer.Tile({ source: new ol.source[source](sourceOptions) }),
                    vectorLayer
                ],
                controls: [],
                view: new ol.View({
                    center: ol.proj.transform([0, 0], dataProjection, featureProjection),
                    zoom: 1
                })
            });

            this.updateSize = _.throttle(this.updateSize.bind(this), 500);
            this.map = map;
            this.vectorSource = vectorSource;
            this.vectorLayer = vectorLayer;

            this.fit();

            const viewport = map.getViewport();
            viewport.setAttribute('tabindex', '-1');
            viewport.setAttribute('data-allow-focus', true);
            this.viewportListeners();

            this.on(window, 'keyup', event => {
                if (event.keyCode === Escape) {
                    viewport.blur();
                }
            });

            this.map.on('click', event => {
                const { pixel, originalEvent } = event;
                const features = this.map.getFeaturesAtPixel(pixel)
                if (features) {
                    if (features.length) {
                        let propertyId;
                        const values = _.uniq(_.compact(features.map(f => {
                            const { field, name } = f.getProperties();
                            if (!propertyId) {
                                propertyId = field;
                            }
                            if (field === propertyId) {
                                return name;
                            }
                        })))
                        // Ignore geohash/within for now
                        if (predicate === 'equal' && propertyId && values.length) {
                            this.handleClick({
                                filters: [{ propertyId, predicate, values }]
                            }, originalEvent.target)
                        }
                    }
                }
            })
            this.setInteractions(false);
        };

        this.fit = function() {
            this.map.getView().fit(this.vectorSource.getExtent());
        };

        this.viewportListeners = function() {
            const viewport = this.map.getViewport();
            this.on(viewport, 'focus', () => { this.setInteractions(true); });
            this.on(viewport, 'blur', () => { this.setInteractions(false); });
        };

        this.setInteractions = function(activate) {
            if (!this.interactionMouseWheel) {
                this.interactionMouseWheel = this.map.getInteractions().getArray().filter(i => {
                    return i instanceof ol.interaction.MouseWheelZoom;
                })
            }

            if (!_.isEmpty(this.interactionMouseWheel)) {
                this.interactionMouseWheel.forEach(i => {
                    if (i.getActive() !== activate) {
                        i.setActive(activate);
                    }
                })
                this.map.getViewport().setAttribute(
                    'title',
                    activate ? '' : i18n('dashboard.map.mousezoom.disabled')
                )
            }
        };

        this.getFeatures = function(geoJson) {
            const reader = new ol.format.GeoJSON();
            const features = reader.readFeatures(geoJson, { dataProjection, featureProjection })
            return features;
        };

        this.getLayer = function(d3, { display = NORMAL, min, max }, source) {
            const newMin = 0.4;
            const opacityRange = [0.4, 0.5];
            const weight = feature => {
                const { amount, weight } = feature.getProperties()
                if (weight) {
                    return weight;
                }
                const calc = (min === max ?
                    0.5 :
                    ((amount - min) / (max - min))
                ) * (1 - newMin) + newMin;
                feature.set('weight', calc, true);
                return calc;
            };
            const styleCache = {};
            const colors = ['#FFFF00', '#FFAB00', '#FF0000'];
            const step = d3.scale.linear().domain([1, colors.length]).range([newMin, 1]);
            const opacity = d3.scale.linear()
                .domain([newMin, 1])
                .range(opacityRange)
            const colorScale = d3.scale.linear()
                .interpolate(d3.interpolateHcl)
                .domain(colors.map((c, i, l) => {
                    if (i === 0) return newMin
                    if (i === l.length - 1) return 1;
                    return step(i + 1)
                }))
                .range(colors);
            const style = feature => {
                const w = weight(feature);
                const index = (255 * w) | 0;
                let style = styleCache[index];
                if (!style) {
                    const color = colorjs(colorScale(w));
                    const colorWithAlpha = color.setAlpha(opacity(w)).toCSS();
                    const colorDarkened = color.darkenByAmount(0.1).toCSS();
                    style = [
                        new ol.style.Style({
                            fill: new ol.style.Fill({ color: colorWithAlpha }),
                            stroke: new ol.style.Stroke({ color: colorDarkened, width: 1 })
                        })
                    ];
                    styleCache[index] = style;
                }
                return style;
            }

            switch (display) {
                case NORMAL:
                    return new ol.layer.Vector({ source, style })

                case HEATMAP:
                    return new ol.layer.Heatmap({ source, weight })

                default:
                    throw new Error('No display of type ' + display + ' found.')
            }
        }

    }
});
