define(['data/web-worker/store/user/actions'], function(userActions) {
    'use strict';

    return withCurrentUser;

    function withCurrentUser() {

        this.after('initialize', function() {
            this.on('didLogout', function() {
                this.setPublicApi('currentUser', undefined);
                this.setPublicApi('socketSourceGuid', undefined);
            });
        });

        this.around('dataRequestCompleted', function(dataRequestCompleted, request) {
            if (isUserMeRequest(request)) {
                var user = request.result;

                this.setPublicApi('currentUser', user, { onlyIfNull: true });
                visalloData.storePromise.then(store => store.dispatch(userActions.putUser({ user })));

                if (user.currentWorkspaceId) {
                    this.setPublicApi('currentWorkspaceId', user.currentWorkspaceId, { onlyIfNull: true });
                }
            } else if (isUserPreferencesUpdate(request)) {
                const { uiPreferences: preferences } = request.result;
                visalloData.currentUser.uiPreferences = preferences;
                this.setPublicApi('currentUser', visalloData.currentUser);
                visalloData.storePromise.then(store => store.dispatch(userActions.putUserPreferences({ preferences })));
            }

            return dataRequestCompleted.call(this, request);
        });
    }

    function isUserPreferencesUpdate(request) {
        return request &&
            request.result &&
            request.result.uiPreferences;
    }

    function isUserPreferenceUpdate(request) {
        return request &&
            request.originalRequest.service === 'user' &&
            request.originalRequest.method === 'preference';
    }

    function isUserMeRequest(request) {
        return request &&
               request.success &&
               request.originalRequest.service === 'user' &&
               request.originalRequest.method === 'me' &&
               request.result;
    }
});
