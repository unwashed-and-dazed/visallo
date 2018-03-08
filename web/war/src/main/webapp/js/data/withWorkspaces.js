define([], function() {
    'use strict';

    return withWorkspaces;

    function withWorkspaces() {
        var workspace,
            undoManagersPerWorkspace = {};

        this.after('initialize', function() {
            var self = this;

            this.fireApplicationReadyOnce = _.once(this.trigger.bind(this, 'applicationReady'));

            this.on('loadCurrentWorkspace', this.onLoadCurrentWorkspace);
            this.on('switchWorkspace', this.onSwitchWorkspace);
            this.on('updateWorkspace', this.onUpdateWorkspace);
            this.on('undo', this.onUndo);
            this.on('redo', this.onRedo);

            visalloData.storePromise.then(store => store.observe(state => state.workspace, (next, prev) => {
                const state = store.getState()
                const oldWorkspace = prev && prev.currentId && prev.byId[prev.currentId];
                const newWorkspace = next && next.currentId && next.byId[next.currentId];
                const changed = newWorkspace && (!oldWorkspace || oldWorkspace.workspaceId !== newWorkspace.workspaceId);

                if (changed) {
                    workspace = {...newWorkspace};
                    this.setPublicApi('currentWorkspaceId', workspace.workspaceId);
                    this.setPublicApi('currentWorkspaceName', workspace.title);
                    this.setPublicApi('currentWorkspaceEditable', workspace.editable);
                    this.setPublicApi('currentWorkspaceCommentable', workspace.commentable);
                    this.trigger('workspaceLoaded', workspace);
                    this.trigger('selectObjects');
                    this.fireApplicationReadyOnce();
                }

                _.each(next.byId, (workspace, id) => {
                    const previousWorkspace = prev.byId[id];
                    const workspaceChanged = !previousWorkspace || (previousWorkspace !== workspace);
                    if (workspaceChanged) {
                        this.setPublicApi('currentWorkspaceName', workspace.title);
                        this.setPublicApi('currentWorkspaceEditable', workspace.editable);
                        this.setPublicApi('currentWorkspaceCommentable', workspace.commentable);
                        this.trigger('workspaceUpdated', { workspace })
                    }
                });

                const deletedKeys = prev && next && Object.keys(_.omit(prev.byId, Object.keys(next.byId)));
                if (deletedKeys) {
                    deletedKeys.forEach(workspaceId => {
                        this.trigger('workspaceDeleted', { workspaceId });
                    })
                }
            }));
        });

        this.onLoadCurrentWorkspace = function(event) {
            var currentWorkspaceId = this.visalloData.currentWorkspaceId;
            this.trigger('switchWorkspace', { workspaceId: currentWorkspaceId });
        };

        this.onSwitchWorkspace = function(event, data) {
            this.setPublicApi('currentWorkspaceId', data.workspaceId);
            Promise.all([
                visalloData.storePromise,
                Promise.require('data/web-worker/store/workspace/actions')
            ]).spread(function(store, workspaceActions) {
                store.dispatch(workspaceActions.setCurrent(data.workspaceId))
            });
        };

        this.onUpdateWorkspace = function(event, data) {
            var self = this,
                triggered = false,
                buffer = _.delay(function() {
                    triggered = true;
                    self.trigger('workspaceSaving', workspace);
                }, 250),
                result,
                legacyKeys = ['entityUpdates', 'entityDeletes'],
                legacy = _.pick(data, legacyKeys);

            if (legacy.length) {
                data = _.omit(data, legacyKeys);
                console.warn('updateWorkspace no longer accepts entity changes');
            }

            if (!_.isEmpty(data)) {
                this.dataRequestPromise.then(function(dataRequest) {
                    dataRequest('workspace', 'save', data)
                        .then(function(data) {
                            clearTimeout(buffer);
                            if (data.saved) {
                                triggered = true;
                            }
                        })
                        .catch(function(e) {
                            console.error(e);
                        })
                        .then(function() {
                            if (triggered) {
                                self.trigger('workspaceSaved', result);
                            }
                        })
                });
            }
        };

        this.onUndo = function() {
            Promise.all([
                visalloData.storePromise,
                Promise.require('data/web-worker/store/undo/actions')
            ]).spread((store, actions) => {
                const scope = this.visalloData.currentWorkspaceId;
                store.dispatch(actions.undoForProduct());
            });
        };

        this.onRedo = function() {
            Promise.all([
                visalloData.storePromise,
                Promise.require('data/web-worker/store/undo/actions')
            ]).spread((store, actions) => {
                const scope = this.visalloData.currentWorkspaceId;
                store.dispatch(actions.redoForProduct());
            });
        };
    }
});
