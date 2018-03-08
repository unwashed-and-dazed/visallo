define(['updeep'], function(u) {
    'use strict';

    return function user(state, { type, payload }) {
        if (!state) return { current: null };

        switch (type) {
            case 'USER_PUT': return put(state, payload.user)
            case 'USER_PUT_PREFS':
                return u({ current: { uiPreferences: payload.preferences }}, state);
            case 'USER_PUT_PREF':
                return u({ current: { uiPreferences: { [payload.name]: `${payload.value}` }}}, state);
        }

        return state
    }

    function put(state, user) {
        const { privileges = [] } = user;
        const userWithPrivHelper = {
            ...user,
            privileges: privileges.sort(),
            privilegesHelper: _.indexBy(privileges)
        };
        return u({ current: userWithPrivHelper }, state);
    }
})


