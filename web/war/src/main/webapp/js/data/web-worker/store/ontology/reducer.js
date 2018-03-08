define(['updeep'], function(u) {
    'use strict';

    return function ontology(state = {}, { type, payload }) {

        switch (type) {
            case 'ONTOLOGY_UPDATE': return update(state, payload);
            case 'ONTOLOGY_PARTIAL_UPDATE': return updatePartial(state, payload);
            case 'ONTOLOGY_IRI_CREATED': return updateIri(state, payload);
            case 'ONTOLOGY_INVALIDATE': return invalidate(state, payload);
            case 'ONTOLOGY_REMOVE_IRIS': return remove(state, payload);
        }

        return state;
    }

    function update(state, payload) {
        const { workspaceId, ...ontology } = payload;
        return u({ [workspaceId]: u.constant(ontology) }, state);
    }

    function updatePartial(state, payload) {
        const { workspaceId, concepts = {}, relationships = {}, properties = {} } = payload;
        return u({
            [workspaceId]: {
                concepts: _.mapObject(concepts, o => u.constant(o)),
                relationships: _.mapObject(relationships, o => u.constant(o)),
                properties: _.mapObject(properties, o => u.constant(o)),
            }
        }, state)
    }

    function remove(state, payload) {
        const { workspaceId, ...iris } = payload;
        const updates = {}
        _.each(iris, (list, type) => {
            if (_.isArray(list) && list.length) {
                updates[type] = u.omit(list);
            }
        })
        return u({ [workspaceId]: updates }, state)
    }

    function invalidate(state, { workspaceIds = [] }) {
        return u(u.omit(workspaceIds), state);
    }

    function updateIri(state, { type, key, error, iri }) {
        if (iri) {
            return u.updateIn(`iris.${type}.${key}`, iri, state);
        }
        return u.updateIn(`iris.${type}.${key}.error`, error, state);
    }
});

