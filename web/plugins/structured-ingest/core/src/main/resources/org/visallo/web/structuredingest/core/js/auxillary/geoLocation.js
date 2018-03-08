define([
    'flight/lib/component',
    '../../templates/auxillary/geoLocation.hbs'
], function(
    defineComponent,
    template) {
    'use strict';

    const LAT_LIKE = ['lat', 'latitude'];
    const LONG_LIKE = ['lon', 'long', 'longitude'];

    return defineComponent(GeoLocationAuxillary);

    function GeoLocationAuxillary() {

        this.defaultAttrs({
            containsSelector: 'select.contains',
            otherSectionSelector: '.otherSection',
            otherColumnSelector: 'select.otherColumn',
            formatSelector: 'select.format'
        });

        this.after('initialize', function() {
            const self = this;
            var { key, hints } = this.attr.mapping;

            if (_.isEmpty(hints)) {
                hints = this.guessGeoColumns();
            }

            this.$node.html(template({
                otherColumn: self.attr.allHeaders.map(function(header, i, headers) {
                    return {
                        value: i,
                        display: header,
                        selected:
                            key === hints.columnLatitude ?
                                hints.columnLongitude === header :
                            key === hints.columnLongitude ?
                                hints.columnLatitude === header : false,
                        disabled: header === key
                    };
                }),
                contains: [
                    { value: 'both', display: 'Latitude and Longitude' },
                    { value: 'latitude', display: 'Latitude' },
                    { value: 'longitude', display: 'Longitude' }
                ].map(function(c) {
                    if ('columnLatitude' in hints &&
                        c.value === 'latitude' &&
                        key === hints.columnLatitude) {
                        c.selected = true;
                    } else if ('columnLongitude' in hints &&
                        c.value === 'longitude' &&
                        key === hints.columnLongitude) {
                        c.selected = true;
                    } else if (c.value === 'both') {
                        c.selected = true;
                    }
                    return c;
                }),
                formats: [
                    { value: 'DEGREES_MINUTES_SECONDS', display: '0° 0\' 00.0" (degrees, minutes, seconds)' },
                    { value: 'DEGREES_DECIMAL_MINUTES', display: '0° 00.000\'  (degrees, decimal minutes)' },
                    { value: 'DECIMAL', display: '00.000°     (decimal degrees)' }
                ].map(function(f, i) {
                    if (f.value === hints.format || (!hints.format && i === 2)) {
                        f.selected = true;
                    }
                    return f;
                })
            }));

            this.on('change', {
                containsSelector: this.onContainsChange,
                otherColumnSelector: this.onOtherColumnChange,
                formatSelector: this.onFormatChange
            })

            this.triggerChange();
        });

        this.onOtherColumnChange = function(event) {
            this.triggerChange();
        };

        this.onContainsChange = function(event) {
            this.triggerChange();
        };

        this.onFormatChange = function(event) {
            this.triggerChange();
        };

        this.triggerChange = function() {
            var self = this,
                contains = this.select('containsSelector').val(),
                otherColumn = this.select('otherColumnSelector').val(),
                otherColumnNumber = parseInt(otherColumn, 10);

            if (isNaN(otherColumnNumber)) {
                otherColumnNumber = undefined;
            }

            this.trigger('addAuxillaryData', _.tap({
                format: this.select('formatSelector').val()
            }, function(data) {
                if (contains === 'latitude') {
                    data.columnLatitude = self.attr.mapping.key
                    data.columnLongitude = self.attr.allHeaders[otherColumnNumber];
                    self.$node.find('.otherType').text('Longitude')
                }
                if (contains === 'longitude') {
                    data.columnLatitude = self.attr.allHeaders[otherColumnNumber];
                    data.columnLongitude = self.attr.mapping.key
                    self.$node.find('.otherType').text('Latitude')
                }
                self.select('otherSectionSelector').toggle(contains !== 'both');
            }));
        };

        this.guessGeoColumns = function() {
            const { key: column } = this.attr.mapping;
            const columns = this.attr.allHeaders;
            const columnsByName = _.groupBy(columns, name => name && name.toLowerCase())
            const test = (expectedList, test) => {
                return _.any(expectedList, n => test && (test.toLowerCase() === n));
            }
            const findColumn = expectedList => {
                return _.find(expectedList, e => {
                    const found = columnsByName[e];
                    if (found && found.length === 1) {
                        return found[0];
                    }
                })
            }
            const hints = {};

            if (column) {
                if (test(LAT_LIKE, column)) {
                    const other = findColumn(LONG_LIKE)
                    if (other) {
                        hints.columnLatitude = column;
                        hints.columnLongitude = other;
                    }
                } else if (test(LONG_LIKE, column)) {
                    const other = findColumn(LAT_LIKE)
                    if (other) {
                        hints.columnLongitude = column;
                        hints.columnLatitude = other;
                    }
                }
            }
            return hints;
        }

    }
});
