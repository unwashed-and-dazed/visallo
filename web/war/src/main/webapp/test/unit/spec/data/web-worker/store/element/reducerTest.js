define([
    // We mock store.js so have to use absolute path here
    '/base/jsc/data/web-worker/store/element/reducer'
], function(reducer) {

    const workspaceId = 'w1';
    const genState = ({
        wId = workspaceId,
        vertices = {},
        edges = {}
    }) => ({
        [wId]: { vertices, edges }
    });

    describe('elementReducer', () => {

        it('should update a vertex\'s edge labels', () => {
            const state = genState({ vertices: {v1: { id: 'v1', edgeLabels: ['label1'] }}});
            const nextState = reducer(state, {
                type: 'ELEMENT_UPDATE_EDGELABELS',
                payload: {
                    workspaceId,
                    vertexLabels: {
                        v1: ['label1', 'label2']
                    }
                }
            });
            nextState.should.deep.equal({
                [workspaceId]: {
                    vertices: {
                        v1: { id: 'v1', edgeLabels: ['label1', 'label2']},
                    },
                    edges: {}
                }
            });
        });

        it('should update focused elements', () => {
            const nextState = reducer({ focusing: { vertexIds: { v2: true }, edgeIds: {}}}, {
                type: 'ELEMENT_SET_FOCUS',
                payload: {
                    vertexIds: ['v1'],
                    edgeIds: ['e1', 'e2']
                }
            });
            nextState.should.deep.equal({
                focusing: {
                    vertexIds: {
                        v1: true
                    },
                    edgeIds: {
                        e1: true,
                        e2: true
                    }
                }
            });
        });

        it('should clear focused elements', () => {
            const nextState = reducer({ focusing: { vertexIds: { v2: true }, edgeIds: { e1: true }}}, {
                type: 'ELEMENT_SET_FOCUS',
                payload: {
                    vertexIds: [],
                    edgeIds: []
                }
            });
            nextState.should.deep.equal({
                focusing: {
                    vertexIds: {},
                    edgeIds: {}
                }
            });
        });

        describe('ELEMENT_UPDATE', () => {

            it('should add new elements', () => {
                const state = genState({});
                const nextState = reducer(state, {
                    type: 'ELEMENT_UPDATE',
                    payload: {
                        workspaceId,
                        vertices: [{ id: 'v1' }],
                        edges: [{ id: 'e1'}]
                    }
                });
                nextState.should.deep.equal({
                    [workspaceId]: {
                        vertices: {
                            v1: { id: 'v1', propertiesByName: {} },
                        },
                        edges: {
                            e1: { id: 'e1', propertiesByName: {} }
                        }
                    }
                });
            });

            it('should remove deleted elements', () => {
                const state = genState({ vertices: {v1: { id: 'v1' }}, edges: {e1: { id: 'e1' }}});
                const nextState = reducer(state, {
                    type: 'ELEMENT_UPDATE',
                    payload: {
                        workspaceId,
                        vertices: [{ id: 'v1', _DELETED: true }],
                        edges: [{ id: 'e1', _DELETED: true }]
                    }
                });
                nextState.should.deep.equal({
                    [workspaceId]: {
                        vertices: {
                            v1: null,
                        },
                        edges: {
                            e1: null
                        }
                    }
                });
            });

            it('should update existing elements', () => {
                const state = genState({ vertices: { v1: { id: 'v1', title: 'a' }}});
                const nextState = reducer(state, {
                    type: 'ELEMENT_UPDATE',
                    payload: {
                        workspaceId,
                        vertices: [{ id: 'v1', title: 'b' }]
                    }
                });
                nextState.should.deep.equal({
                    [workspaceId]: {
                        vertices: {
                            v1: { id: 'v1', title: 'b', propertiesByName: {} },
                        },
                        edges: {}
                    }
                });
            });

            it('should return the same element if there are no changes', () => {
                const state = genState({
                    vertices: {
                        v1: {
                            id: 'v1',
                            properties: [{ name: 'p1' }, { name: 'p2' }, { name: 'p3' }],
                            propertiesByName:  { p1: [{ name: 'p1' }], p2: [{ name: 'p2' }], p3: [{ name: 'p3' }]}
                        }
                    }
                });
                const previousVertex = state[workspaceId].vertices['v1'];
                const nextState = reducer(state, {
                    type: 'ELEMENT_UPDATE',
                    payload: {
                        workspaceId,
                        vertices: [{
                            id: 'v1',
                            properties: [{ name: 'p1' }, { name: 'p2' }, { name: 'p3' }]
                        }]
                    }
                });
                const nextVertex = nextState[workspaceId].vertices['v1'];

                nextState.should.deep.equal({
                    [workspaceId]: {
                        vertices: {
                            v1: {
                                id: 'v1',
                                properties: [{ name: 'p1' }, { name: 'p2' }, { name: 'p3' }],
                                propertiesByName:  { p1: [{ name: 'p1' }], p2: [{ name: 'p2' }], p3: [{ name: 'p3' }]}}
                        },
                        edges: {}
                    }
                });
                nextVertex.should.equal(previousVertex);
            });
        });
    });
});
