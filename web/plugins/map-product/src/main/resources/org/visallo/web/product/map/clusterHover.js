define(['openlayers', 'util/mapConfig'], function(ol, mapConfig) {

    let overlay;
    let innerMap;
    let layer;
    let element;

    return {
        show(ol, map, cluster, styleFn) {
            if (overlay) {
                map.removeOverlay(overlay);
            }

            const coordinates = cluster.get('coordinates');
            const extent = ol.extent.boundingExtent(coordinates)
            const size = getSize(extent)
            const coord = cluster.getGeometry().getCoordinates();
            const pixel = map.getPixelFromCoordinate(coord);
            const offset = 20;
            const flip = pixel[1] < (size[1] + offset)

            if (!element) {
                element = $('<div class="popover"></div>').css({ position: 'relative'}).show()
                element.append('<div class="arrow"></div>')
                element.append('<div class="popover-content" style="padding:0;border-radius: 4px; overflow: hidden;"></div>')
            }
            element.toggleClass('top', !flip).toggleClass('bottom', flip)
            element.find('.popover-content').css({ width: size[0], height: size[1] })

            overlay = new ol.Overlay({
                offset: [0, offset * (flip ? 1 : -1)],
                element: element[0],
            });

            element.show()
            map.addOverlay(overlay);

            overlay.setPosition(coord);
            overlay.setPositioning(flip ? 'top-center' : 'bottom-center');

            if (!innerMap) {
                const { map: _map, layer: _layer } = setupMap(styleFn);
                innerMap = _map;
                layer = _layer;
            }

            innerMap.setSize(size);

            const maxRadius = cluster.get('features').reduce((max, f) => {
                return Math.max(max, f.get('_nodeRadius'))
            }, 0);
            innerMap.getView().fit(extent, {
                size,
                maxZoom: 9,
                padding: [maxRadius, maxRadius, maxRadius, maxRadius]
            })
            layer.setStyle(styleFn);

            const source = layer.getSource();
            source.clear();
            source.addFeatures(cluster.get('features'))
        },

        hide(ol, map) {
            if (overlay) {
                map.removeOverlay(overlay);
            }
        }
    }

    function getSize(extent) {
        const aspect = (extent[2] - extent[0]) / (extent[3] - extent[1]) || 1
        let size = [200, 200]
        if (aspect > 1) {
            size[1] = Math.max(150, size[0] * (1 / aspect))
        } else {
            size[0] = Math.max(150, size[1] * aspect);
        }
        return size;
    }

    function setupMap() {
        const { source, sourceOptions } = mapConfig();
        let baseLayerSource = new ol.source[source](sourceOptions)
        const layer = new ol.layer.Vector({
            source: new ol.source.Vector({
                features: []
            })
        })
        return {
            map: new ol.Map({
                controls: [],
                layers: [
                    new ol.layer.Tile({ source: baseLayerSource }),
                    layer,
                ],
                target: element.find('.popover-content')[0]
            }),
            layer
        };
    }
})
