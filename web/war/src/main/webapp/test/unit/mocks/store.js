define(['util/promise', '/base/test/unit/mocks/ontologyJson'], function(Promise, ontologyJson) {
    var promise;
    var ontology = {};
    ontology[publicData.currentWorkspaceId] = {
        concepts: _.indexBy(ontologyJson.concepts, 'id'),
        relationships: _.indexBy(ontologyJson.relationships, 'title'),
        properties: _.indexBy(ontologyJson.properties, 'title')
    }

    return {
        getStore: function() {
            return {
                getState: function() {
                    return {
                        workspace: {
                            currentId: publicData.currentWorkspaceId
                        },
                        ontology
                    };
                },
                subscribe: function() { },
                observe: function(handler) { handler(this.getState(), this.getState())}
            }
        },
        getOrWaitForNestedState: function(callback){
            if (promise) return promise.then(function(o) {
                return JSON.parse(JSON.stringify(o));
            });
            var copied = callback({ ontology })
            return (promise = Promise.resolve(copied));
        }
    }
})

