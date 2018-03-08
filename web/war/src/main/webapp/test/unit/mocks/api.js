define([], function() {
    // Avoids requiring all api components that have UI dependencies
    return {
        connect() {
            return new Promise(r => {
                require([
                    'util/formatters'
                ], (formatters) => {
                    r({
                        formatters
                    })
                })
            })
        }
    }
})
