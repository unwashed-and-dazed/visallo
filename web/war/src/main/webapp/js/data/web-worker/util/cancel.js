define([], function() {
    const requestCache = {};

    return function cancelPreviousByHash(promise, hash) {
        if (!_.isString(hash)) throw new Error('Cancel hash must be a string')
        if (!promise || !_.isFunction(promise.cancel) || !_.isFunction(promise.then)) {
            throw new Error('Promise must be cancellable')
        }

        const previous = requestCache[hash];
        if (previous) {
            previous.cancel();
        }

        requestCache[hash] = promise;

        promise.then(() => {
            delete requestCache[hash];
        })

        return promise;
    }
})
