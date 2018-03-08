define([
    'flight/lib/component',
    'util/formatters',
    './withRenderer',
    './withMapTiles',
    'd3'
], function(
    defineComponent,
    F,
    withRenderer,
    withMapTiles,
    d3) {
    'use strict';

    return defineComponent(Choropleth, withRenderer, withMapTiles);

    function Choropleth() {

        this.processData = function(data) {
            var self = this,
                results = data.root[0].buckets,
                zipCodeBoundary = function(params) {
                    return self.dataRequest('dashboard', 'requestData', '/zip-code-boundary', params);
                };

            if (results && results.length) {
                const bucketsByName = _.indexBy(results, 'name');
                const flipCoordinates = c => [c[1], c[0]];
                return zipCodeBoundary({ zipCode: _.pluck(results, 'name') })
                           .then(function(zipCodes) {
                               var min = Infinity,
                                   max = -Infinity,
                                   features = zipCodes.features.map(function({ coordinates: rings, zipCode }) {
                                       const bucket = bucketsByName[zipCode];
                                       const amount = bucket ? bucket.value.count : 0;

                                       min = Math.min(min, amount);
                                       max = Math.max(max, amount);

                                       return {
                                           type: 'Feature',
                                           geometry: {
                                               type: 'Polygon',
                                               coordinates: rings.map(r => r.map(flipCoordinates))
                                           },
                                           properties: {
                                               ...bucket,
                                               label: zipCode,
                                               amount
                                           }
                                       }
                                   });

                               return {
                                   geoJson: {
                                       type: 'FeatureCollection',
                                       features
                                   },
                                   min,
                                   max,
                                   predicate: 'equal',
                                   display: 'normal'
                               };
                           });
            }
            return null;
        };
    }
});
