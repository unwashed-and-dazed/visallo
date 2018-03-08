define([
    'flight/lib/component',
    'util/formatters',
    './withRenderer',
    './withMapTiles',
    'openlayers',
    'd3'
], function(
    defineComponent,
    F,
    withRenderer,
    withMapTiles,
    ol,
    d3) {
    'use strict';

    return defineComponent(Geohash, withRenderer, withMapTiles);

    function Geohash() {

        this.processData = function(data) {
            var results = data.root[0].buckets;
            if (results && results.length) {
                var min = Infinity,
                    max = -Infinity,
                    extent = ol.extent.createEmpty(),
                    features = _.map(results, function(bucket) {
                        const { field, name } = bucket;
                        const northWest = bucket.value.cell.northWest;
                        const southEast = bucket.value.cell.southEast;
                        const amount = bucket.value.count || 0;

                        max = Math.max(amount, max);
                        min = Math.min(amount, min);

                        ol.extent.createOrUpdateFromCoordinates([
                            [northWest.longitude, northWest.latitude],
                            [southEast.longitude, northWest.latitude],
                            [southEast.longitude, southEast.latitude],
                            [northWest.longitude, southEast.latitude],
                            [northWest.longitude, northWest.latitude]
                        ], extent);

                        return {
                              type: 'Feature',
                              geometry: {
                                  type: 'Point',
                                  coordinates: ol.extent.getCenter(extent)
                              },
                            properties: { amount, name, field }
                          };
                    });

                return {
                    geoJson: {
                        type: 'FeatureCollection',
                        features
                    },
                    min,
                    max,
                    predicate: 'within',
                    display: 'heatmap'
                };
            }
            return null;
        };
    }
});
