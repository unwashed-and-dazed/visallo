define([], function() {
    'use strict';

    return (createStore) => (reducer, preloadedState, enhancer) => {
        const store = createStore(reducer, preloadedState, enhancer)
        store.observe = observeStore(store);

        return store;
    }

    function observeStore(store) {
        return function(selector, handler) {
            let previousState;

            const handleChange = () => {
                const onChange = handler || selector;
                const newState = handler ? selector(store.getState()) : store.getState();

                if (newState !== previousState) {
                    onChange(newState, previousState);
                    previousState = newState;
                }
            }

            const unsubscribe = store.subscribe(handleChange);
            handleChange();
            return unsubscribe;
        }
    }
});
