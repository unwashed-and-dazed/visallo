define([], function() {

    // WeakMap API, but for storing value types
    function ValueMap() {
        this.map = {};
    }
    ValueMap.prototype.get = function(key) {
        return this.map[key];
    }
    ValueMap.prototype.set = function(key, value) {
        this.map[key] = value;
    }
    ValueMap.prototype.delete = function(key) {
        delete this.map[key];
    }

    DeepObjectCache.prototype.getOrUpdate = getOrUpdate;
    DeepObjectCache.prototype.clear = clear;

    return DeepObjectCache;

    /*
     * Cache that uses weak maps to cache results of functions given object
     * arguments.
     *
     * Useful for caching calls to registry extension functions given arguments.
     *
     * Arguments can be objects, or primitives (strings, numbers, booleans.)
     *
     * Input objects must be immutable otherwise changes won't be detected /
     * reevaluated. Comparisons are done using `===`, not `_.isEqual`.
     *
     *     var c = new DeepObjectCache();
     *     c.getOrUpdate(expensiveFn, input1, input2);
     *     c.clear()
     *     // Calls expensiveFn(input1, input2) once until inputs or arity changes
     */
    function DeepObjectCache() {
        if (this === window) throw new Error('Must instantiate cache with new')
    }

    function clear() {
        if (this.rootMap) {
            this.rootMap = null;
        }
    }

    function getOrUpdate(fn, ...args) {
        if (!_.isFunction(fn)) throw new Error('fn must be a function');
        if (!args.length) throw new Error('Must have at least one argument');

        if (!this.rootMap) this.rootMap = createCache(args[0]);

        return _getOrUpdate(this.rootMap, [fn, ...args], reevaluate)

        function reevaluate() {
            return fn.apply(null, args);
        }
    }

    function isCache(obj) {
        return obj instanceof WeakMap || obj instanceof ValueMap;
    }

    function createCache(obj) {
        return _.isObject(obj) && !_.isString(obj) ? new WeakMap() : new ValueMap();
    }

    function _getOrUpdate(cache, keyObjects, reevaluate) {
        if (keyObjects.length === 0) {
            return cache
        }

        const nextKey = keyObjects.shift();
        let nextObject = cache.get(nextKey);
        if (nextObject) {
            // Check for arity changes and clear
            if (nextObject instanceof WeakMap && keyObjects.length === 0) {
                nextObject = reevaluate();
            } else if (!isCache(nextObject) && keyObjects.length) {
                cache.delete(nextKey)
                nextObject = createCache(nextKey);
            }
        } else {
            nextObject = keyObjects.length ? createCache(keyObjects[0]) : reevaluate();
        }

        cache.set(nextKey, nextObject);

        return _getOrUpdate(nextObject, keyObjects, reevaluate);
    }
})
