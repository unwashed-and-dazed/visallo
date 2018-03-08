/*
 * Replaces prop-types library with no-check shims in production,
 * Normally webpack would handle this for us, but not using that yet...
 */
define(visalloEnvironment.dev ? ['react-proptypes-dev'] : [], function(PropTypes) {
    const PropTypeShims = getPropTypeShims();

    if (PropTypes) {
        if (!shimPropTypesSameAsReal(PropTypeShims, PropTypes)) {
            // We shim the PropTypes in production for performance, but in dev
            // also make sure our shims match what react provides.
            const realKeys = Object.keys(PropTypes)
            const shimKeys = Object.keys(PropTypeShims)
            const missing = _.difference(realKeys, shimKeys).join(', ');
            const extra = _.difference(shimKeys, realKeys).join(', ');
            if (missing.length) console.warn('PropTypes shim is missing:', missing);
            if (extra.length) console.warn('PropTypes shim has extras:', extra);
            throw new Error('PropTypes that are defined for production differ from those in react');
        }
        return PropTypes;
    }

    return PropTypeShims;

    function shimPropTypesSameAsReal(shims, real) {
        const shimKeys = Object.keys(shims);
        const realKeys = Object.keys(real);
        return shimKeys.length === realKeys.length &&
            _.intersection(shimKeys, realKeys).length === shimKeys.length;
    }

    function getPropTypeShims() {
        const shim = function() {};
        shim.isRequired = shim;
        const getShim = function() { return shim; }
        const PropTypeShims = {
            any: shim,
            array: shim,
            arrayOf: getShim,
            bool: shim,
            checkPropTypes: shim,
            element: shim,
            exact: getShim,
            func: shim,
            instanceOf: getShim,
            node: shim,
            number: shim,
            object: shim,
            objectOf: getShim,
            oneOf: getShim,
            oneOfType: getShim,
            shape: getShim,
            string: shim,
            symbol: shim
        };
        PropTypeShims.PropTypes = PropTypeShims;
        return PropTypeShims;
    }
})
