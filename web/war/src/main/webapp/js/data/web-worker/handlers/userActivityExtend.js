/* globals ajaxFilters:false */
define(['../util/ajax'], function(ajax) {
    'use strict';

    const now = () => new Date().getTime();

    let nextExpiration;
    let defaultExpiration;
    let scheduledTimer;
    let sending = false;

    ajaxFilters.post.push((request, json, options) => {
        const expiration = request.getResponseHeader('Visallo-Auth-Token-Expiration')
        if (expiration) {
            nextExpiration = now() + parseInt(expiration, 10);
        }
    })

    return function({ init = false, defaultExpiration: de, schedule = true }) {
        if (!init) {
            if (!defaultExpiration) {
                defaultExpiration = de;
            }
            if (nextExpiration && defaultExpiration) {
                sendOrScheduleHeartBeat(schedule);
            }
        }
    }

    function sendHeartBeat() {
        const change = val => { sending = val; }
        change(true)
        ajax('GET', '/user/heartbeat')
            .catch(error => {
                dispatchMain('sessionTimeoutActivityHeartBeatFailed');
            })
            .finally(() => change(false))
    }

    function sendOrScheduleHeartBeat(schedule) {
        clearTimeout(scheduledTimer)
        if (sending) {
            return false;
        }

        let nearlyExpired = (defaultExpiration / 2 - 5000)
        const timeLeft = nextExpiration - now();
        const expiringSoon = timeLeft < nearlyExpired;

        if (expiringSoon) {
            sendHeartBeat();
        } else if (schedule) {
            const scheduled = timeLeft - nearlyExpired;
            scheduledTimer = setTimeout(sendHeartBeat, scheduled)
        }
    }
});
