
define([
    'flight/lib/component',
    './stringTpl.hbs',
    'util/vertex/formatters',
    './withPropertyField'
], function(defineComponent, template, F, withPropertyField) {
    'use strict';

    return defineComponent(StringField, withPropertyField);

    function StringField() {

        this.after('initialize', function() {
            this.$node.html(template(_.extend({}, this.attr, {
                textarea: this.attr.property.displayType === 'textarea' || this.attr.property.displayType === 'longText'
            })));
        });

        this.isValid = function(value) {
            var name = this.attr.property.title;

            return _.isString(value) &&
                value.length > 0 &&
                F.vertex.singlePropValid(value, name);
        };

        this.setValue = function(value) {
            if (_.isObject(value)) {
                value = JSON.stringify(value);
            }

            this.select('inputSelector').val(value);
        };

        this.getValue = function() {
            let value = this.select('inputSelector').val();

            if (_.isObject(value)) {
                value = JSON.stringify(value);
            }

            return value.trim();
        };
    }
});
