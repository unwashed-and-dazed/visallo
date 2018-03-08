define(['util/promise'], function(Promise) {
    return Promise.require('../test/unit/mocks/ontologyJson')
        .then(function(json) {
            window.ONTOLOGY_JSON = json;
            return Promise.require('data/web-worker/services/ontology')
        })
        .then(function(ontology) {
            return ontology.ontology()
        });
});
