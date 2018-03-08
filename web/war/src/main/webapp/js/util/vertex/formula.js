define([], function() {
    'use strict';

    return formulaFunction;

    function formulaFunction(formula, vertex, V, optionalKey, optionalOpts) {
        if (!optionalOpts) {
            optionalOpts = {};
        }
        optionalOpts = _.extend({}, optionalOpts, { ignoreDisplayFormula: true });

        var prop = function(name) { return V.prop(vertex, name, optionalKey, optionalOpts); },
            propRaw = function(name) { return V.propRaw(vertex, name, optionalKey, optionalOpts); },
            longestProp = function(optionalName) { return V.longestProp(vertex, optionalName); },
            props = function (name) { return V.props(vertex, name, optionalKey ); }

        try {

            // If the formula is an expression wrap and return it
            if (formula.indexOf('return') === -1) {
                formula = 'return (' + formula + ')';
            }

            var scope = _.extend({}, optionalOpts.additionalScope || {}, {
                prop: prop,
                dependentProp: prop,
                propRaw: propRaw,
                longestProp: longestProp,
                props: props
            });

            if (V.isEdge(vertex)) {
                scope.edge = vertex;
            } else {
                scope.vertex = vertex;
            }

            var keys = [],
                values = [];

            _.each(scope, function(value, key) {
                values.push(value);
                keys.push(key);
            });

            /*eslint no-new-func:0*/
            return (new Function(keys.join(','), formula)).apply(null, values);
        } catch(e) {
            console.warn('Unable to execute formula: ' + formula + ' Reason: ', e);
        }
    }
});
