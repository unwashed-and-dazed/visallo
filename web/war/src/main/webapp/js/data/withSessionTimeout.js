define([], function() {

    const SecondsBeforeTimeout = 30;
    const Key = 'inactivityLogoutDate';
    const ConfigKey = 'auth.token.expiration_minutes';
    const SecondsToMillis = 1000;
    const MinutesToMillis = SecondsToMillis * 60;
    const UserInitiatedLogoutHint = -1;
    const MinumumWarning = 5 * SecondsToMillis;
    const warnAboutWriting = _.once(error => {
        console.warn(`Unable to write to localStorage,
inactivity timeout and logout might happen if multiple windows
are open and inactive.`)
    });

    return withSessionTimeout;

    function withSessionTimeout() {

        this.after('initialize', function() {
            this.worker.postMessage({ type: 'userActivityExtend', init: true })

            this.on(document, 'willLogout', () => {
                if (!this.sessionTimeoutWillLogout) {
                    this.sessionTimeoutWillLogout = true;
                    this.sessionTimeoutUpdateExpiration(UserInitiatedLogoutHint);
                }
            })

            this.on('applicationReady', _.once(() => {
                Promise.all([
                    Promise.require('util/sessionExpirationOverlay'),
                    this.dataRequestPromise
                        .then(dr => dr('config', 'properties'))
                        .then(props => {
                            return parseFloat(props[ConfigKey], 10);
                    })
                ]).spread((overlay, minutes) => {
                    this.Overlay = overlay;
                    if (minutes) {
                        this.defaultExpiration = minutes * 60 * 1000;
                        this.sessionTimeoutListeners();
                        this.sessionTimeoutPushUserActivityExtend({ schedule: false });
                    }
                })
            }));
        });

        this.sessionTimeoutLogout = function({ userInitiatedLogout = false } = {}) {
            if (!this.sessionTimeoutWillLogout) {
                this.sessionTimeoutWillLogout = true;
                require(['util/offlineOverlay'], Offline => {
                    if (!$(document).lookupComponent(Offline)) {
                        let message = userInitiatedLogout ? null : i18n('visallo.session.expired')
                        $(document).trigger('logout', { message });
                    }
                });
            }
        };

        this.sessionTimeoutActivityHeartBeatFailed = function() {
            this.sessionTimeoutLogout();
        };

        this.sessionTimeoutUpdateExpiration = function(millis) {
            this.ignoreStorageUpdates = true;
            try {
                if (millis) {
                    localStorage.setItem(Key, String(millis));
                } else {
                    localStorage.removeItem(Key);
                }
            } catch(error) {
                warnAboutWriting(error)
            }
            this.ignoreStorageUpdates = false;
        };

        this.sessionTimeoutGetExpiration = function() {
            const millis = localStorage.getItem(Key);
            if (millis) {
                return parseInt(millis, 10);
            }
        }

        this.sessionTimeoutPushUserActivityExtend = function({ schedule = true } = {}) {
            if (!this.sessionTimeoutWillLogout) {
                this.sessionTimeoutUpdateExpiration(Date.now() + this.defaultExpiration);
                this.sessionTimeoutWait();

                this.worker.postMessage({
                    type: 'userActivityExtend',
                    defaultExpiration: this.defaultExpiration,
                    schedule
                });
            }
        };

        this.sessionTimeoutWait = function() {
            this.warningUser = false;
            this.Overlay.teardownAll();
            if (this.inactiveLogoutTimer) {
                clearTimeout(this.inactiveLogoutTimer)
            }

            const expirationMillis = this.sessionTimeoutGetExpiration();
            if (expirationMillis) {
                if (expirationMillis === UserInitiatedLogoutHint) {
                    this.sessionTimeoutLogout({ userInitiatedLogout: true })
                } else {
                    const expiration = expirationMillis - Date.now();
                    const defaultWarningDurationMillis = SecondsBeforeTimeout * SecondsToMillis;
                    const warn = expiration - defaultWarningDurationMillis;

                    if (expiration < MinumumWarning) {
                        this.sessionTimeoutLogout();
                    } else {
                        this.inactiveLogoutTimer = setTimeout(() => {
                            this.warningUser = true;
                            clearTimeout(this.inactiveLogoutTimer)

                            const warningDuration = Math.min(expirationMillis - Date.now(), defaultWarningDurationMillis);
                            if (warningDuration < MinumumWarning) {
                                this.sessionTimeoutLogout();
                            } else {
                                this.Overlay.attachTo(document)
                                this.inactiveLogoutTimer = setTimeout(() => {
                                    this.sessionTimeoutLogout();
                                }, warningDuration)
                            }
                        }, Math.max(0, warn));
                    }
                }
            }
        };

        this.sessionTimeoutListeners = function() {
            const throttledActivity = _.throttle(this.sessionTimeoutPushUserActivityExtend.bind(this), 1000, { leading: false })

            this.on(window, 'mousedown mouseup keydown keyup', event => {
                if (this.warningUser) {
                    const eventFinished = event.type === 'mouseup' || event.type === 'keyup';
                    const isButton = $(event.target).is('.session-continue');
                    if (eventFinished && isButton) {
                        this.sessionTimeoutPushUserActivityExtend();
                    }
                } else {
                    throttledActivity();
                }
            });
            this.on(window, 'storage', this.onSessionTimeoutStorageEvent);
        };

        this.onSessionTimeoutStorageEvent = function(event) {
            const { key } = event.originalEvent;
            if (key === Key && !this.ignoreStorageUpdates && !this.sessionTimeoutWillLogout) {
                // IE returns the old value unless we defer
                _.defer(() => {
                    this.sessionTimeoutWait()
                })
            }
        };

    }
});
