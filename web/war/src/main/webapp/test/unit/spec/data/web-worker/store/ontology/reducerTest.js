define([
    // We mock store.js so have to use absolute path here
    '/base/jsc/data/web-worker/store/ontology/reducer'
], function(reducer) {

    const workspaceId = 'w1';

    describe('ontologyReducer', () => {

        it('should update ontology', () => {
            const nextState = reducer(undefined, {
                type: 'ONTOLOGY_UPDATE',
                payload: {
                    workspaceId,
                    concepts: {
                        x: {displayName: 'x'},
                        y: {displayName: 'y'}
                    }
                }
            });
            nextState.should.deep.equal({
                [workspaceId]: {
                    concepts: {
                        x: {displayName: 'x'},
                        y: {displayName: 'y'}
                    }
                }
            });

            const finalState = reducer(nextState, {
                type: 'ONTOlOGY_PARTIAL_UPDATE',
                payload: {
                    workspaceId,
                    concepts: {
                        y: {displayName: 'y'}
                    }
                }
            })

            finalState.should.deep.equal({
                [workspaceId]: {
                    concepts: {
                        x: {displayName: 'x'},
                        y: {displayName: 'y'}
                    }
                }
            });

            finalState[workspaceId].concepts.should.equal(
                nextState[workspaceId].concepts
            )
        })

        it('should remove from ontology', () => {
            let state = {
                [workspaceId]: {
                    concepts: {
                        x: {displayName: 'x'},
                        y: {displayName: 'y'}
                    },
                    relationships: {
                        x: {displayName: 'x'},
                        y: {displayName: 'y'}
                    },
                    properties: {
                        x: {displayName: 'x'},
                        y: {displayName: 'y'}
                    }
                }
            }
            const nextState = reducer(state, {
                type: 'ONTOLOGY_REMOVE_IRIS',
                payload: {
                    workspaceId,
                    concepts: ['y']
                }
            });
            nextState.should.deep.equal({
                [workspaceId]: {
                    concepts: {
                        x: {displayName: 'x'}
                    },
                    relationships: {
                        x: {displayName: 'x'},
                        y: {displayName: 'y'}
                    },
                    properties: {
                        x: {displayName: 'x'},
                        y: {displayName: 'y'}
                    }
                }
            });
        })

    })
})
