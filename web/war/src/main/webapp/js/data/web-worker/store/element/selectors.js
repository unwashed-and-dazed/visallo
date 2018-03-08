define(['reselect'], function({ createSelector }) {

    const getWorkspaceId = (state) => state.workspace.currentId;
    const getRoot = (state) => state.element
    const getElements = createSelector([getWorkspaceId, getRoot], (workspaceId, elements) => {
        return (workspaceId && workspaceId in elements) ? elements[workspaceId] : {};
    });
    const getVertices = createSelector([getElements], elements => elements.vertices || {})
    const getEdges = createSelector([getElements], elements => elements.edges || {})

    return {
        getElements,
        getVertices,
        getEdges
    }
})
