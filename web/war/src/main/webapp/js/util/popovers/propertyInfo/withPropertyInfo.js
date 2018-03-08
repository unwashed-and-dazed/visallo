define([
    'util/withDataRequest'
], function(withDataRequest) {
    'use strict';

    var _PropertyInfo;

    return withPropertyInfo;

    function withPropertyInfo() {

        this.after('initialize', function() {
            this.on(document, 'objectsSelected', function() {
                if (_PropertyInfo) _PropertyInfo.teardownAll();
            })
        })

        this.showPropertyInfo = function(button, data, property) {
            var $target = $(button),
                shouldOpen = $target.lookupAllComponents().length === 0;

            Promise.all([
                Promise.require('util/popovers/propertyInfo/propertyInfo'),
                withDataRequest.dataRequest('ontology', 'properties')
            ]).done(function(results) {
                var PropertyInfo = results.shift(),
                    ontologyProperties = results.shift(),
                    ontologyProperty = ontologyProperties && property && property.name && ontologyProperties.byTitle[property.name];

                _PropertyInfo = PropertyInfo;
                if (shouldOpen) {
                    PropertyInfo.teardownAll();
                    PropertyInfo.attachTo($target, {
                        data: data,
                        property: property,
                        preferredPosition: 'below',
                        ontologyProperty: ontologyProperty
                    });
                } else {
                    $target.teardownComponent(PropertyInfo);
                }
            });
        };

        this.hidePropertyInfo = function(button) {
            var $target = $(button);

            require(['util/popovers/propertyInfo/propertyInfo'], function(PropertyInfo) {
                $target.teardownComponent(PropertyInfo);
            });
        }

    }
});
