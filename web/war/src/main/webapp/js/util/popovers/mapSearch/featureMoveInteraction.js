define(['openlayers'], function(ol) {
    'use strict';

    const SPHERE = new ol.Sphere(6378137);
    const ANIMATION_DURATION = 200;
    const RESIZE_RADIUS = 6;
    const createCircle = (center, radius) => {
        const centerLonLat = ol.proj.toLonLat(center);
        return ol.geom.Polygon.circular(SPHERE, centerLonLat, radius, 64)
            .transform('EPSG:4326', 'EPSG:3857');
    }

    function FeatureMoveInteraction(options = {}) {
        ol.interaction.Pointer.call(this, {
            handleEvent: this.handle.bind(this)
        });
        this.circleCenter = options.center;
        this.circleRadius = options.radius;
        this.shouldFit = options.fit !== false;
        this.condition_ = options.condition ?
            options.condition : ol.events.condition.primaryAction;
    }
    ol.inherits(FeatureMoveInteraction, ol.interaction.Pointer);

    FeatureMoveInteraction.prototype.fit = function() {
        const map = this.getMap();
        const view = map.getView();

        view.fit(this.circleFeature.getGeometry().getExtent())
    }

    FeatureMoveInteraction.prototype.setMap = function(map) {
        if (!map) {
            this.getMap().removeLayer(this.layer)
        }

        ol.interaction.Pointer.prototype.setMap.apply(this, arguments);

        if (!map) return;

        var center = this.circleCenter,
            extent = map.getView().calculateExtent(map.getSize()),
            circleRadius = this.circleRadius || ol.extent.getWidth(extent) * 0.1 / 2;

        if (!this.circleRadius) {
            this.circleRadius = circleRadius;
        }

        var circleGeometry = createCircle(center, circleRadius),
            resizeGeometry = new ol.geom.Point(this.calculateResizePoint(circleGeometry)),
            circleFeature = new ol.Feature(circleGeometry),
            resizeFeature = new ol.Feature(resizeGeometry);

        circleFeature.setId('circle');
        resizeFeature.setId('resize');

        this.circleFeature = circleFeature;
        this.resizeFeature = resizeFeature;

        var vectorSource = new ol.source.Vector({
                features: [circleFeature, resizeFeature],
            }),
            circleLayer = new ol.layer.Vector({
                source: vectorSource,
                style: function(feature) {
                    var fill = new ol.style.Fill({ color: 'rgba(255,255,255,0.4)' });
                    var stroke = new ol.style.Stroke({ color: '#3399CC', width: 1.25 });
                    if (feature.getId() === 'resize') {
                        return [new ol.style.Style({
                            image: new ol.style.Circle({
                                radius: RESIZE_RADIUS,
                                stroke: new ol.style.Stroke({
                                    color: stroke.getColor(),
                                    width: 2
                                }),
                                fill: new ol.style.Fill({
                                    color: 'white'
                                })
                            })
                        })]
                    } else {
                        return [new ol.style.Style({
                            image: new ol.style.Circle({
                                fill: fill,
                                stroke: stroke,
                                radius: 5
                            }),
                            fill: fill,
                            stroke: stroke
                        })]
                    }
                }
            });

        this.layer = circleLayer;
        map.addLayer(circleLayer);

        if (this.shouldFit) {
            this.fit();
        }
    }

    FeatureMoveInteraction.prototype.getRegion = function() {
        return { center: this.circleCenter, radius: this.circleRadius / 1000 };
    }

    FeatureMoveInteraction.prototype.calculateResizePoint = function(geometry) {
        const circleRings = geometry.getCoordinates();
        if (circleRings && circleRings.length === 1) {
            const coordinates = circleRings[0];
            return coordinates[Math.trunc(coordinates.length / 3)]
        }
    }

    FeatureMoveInteraction.prototype.updateGeometry = function(center, radius) {
        const geometry = createCircle(center, radius);
        const point = this.calculateResizePoint(geometry);

        this.circleFeature.setGeometry(geometry)

        if (point) {
            this.resize.setCoordinates(point);
        }
    }

    FeatureMoveInteraction.prototype.handle = function(e) {
        var handled = false;
        if (this[e.type]) {
            handled = this[e.type].apply(this, arguments);
        }
        return ol.interaction.Pointer.handleEvent.call(this, e) && !handled;
    }

    FeatureMoveInteraction.prototype.pointerdown = function(event) {
        var extent = ol.extent.createEmpty(),
            mapDistance = RESIZE_RADIUS * this.getMap().getView().getResolution();

        ol.extent.createOrUpdateFromCoordinate(event.coordinate, extent);
        ol.extent.buffer(extent, mapDistance, extent);

        var features = this.layer.getSource().getFeaturesInExtent(extent);
        if (features.length) {
            this.startCoordinate = event.coordinate;
            this.resize = this.resizeFeature.getGeometry()

            if (features.length === 1 && features[0].getId() === 'circle') {
                this.center = this.circleCenter;
                this.state = 'down'
            } else {
                this.center = [...this.resize.getCoordinates()];
                this.state = 'resize'
            }
            return true;
        }
    }

    FeatureMoveInteraction.prototype.pointermove = function(event) {
        if (!this.state) return;

        const delta = subtractCoordinates(event.coordinate, this.startCoordinate);
        const newCenter = addCoordinates(this.center, delta);

        if (this.state === 'down') {
            this.circleCenter = newCenter;
            this.updateGeometry(this.circleCenter, this.circleRadius)
            var centerChange = new ol.events.Event('centerChanged');
            centerChange.newCenter = newCenter;
            this.dispatchEvent(centerChange);
            return true;
        } else if (this.state === 'resize') {
            const newRadius = new ol.geom.LineString([newCenter, this.circleCenter]).getLength();
            this.circleRadius = newRadius
            this.updateGeometry(this.circleCenter, this.circleRadius)
            var radiusChange = new ol.events.Event('radiusChanged');
            radiusChange.newRadius = newRadius / 1000;
            this.dispatchEvent(radiusChange);
            return true;
        }
    }

    FeatureMoveInteraction.prototype.pointerup = function(event) {
        this.state = null;
    }

    return FeatureMoveInteraction;

    function subtractCoordinates(c1, c2) {
        return c1.map((c, i) => c - c2[i]);
    }

    function addCoordinates(c1, c2) {
        return c1.map((c, i) => c + c2[i])
    }
});
