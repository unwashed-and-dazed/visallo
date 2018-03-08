
DeepObjectCache.prototype.getOrUpdate = function(fn, ...args) {
    return fn.apply(undefined, args);
};

DeepObjectCache.prototype.clear = function() {};

export default function DeepObjectCache() {}

