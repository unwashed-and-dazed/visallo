define(['configuration/plugins/registry'], function(registry) {
    'use strict';

    registry.registerExtension('org.visallo.dashboard.item', {
        title: i18n('dashboard.savedsearches.title'),
        description: i18n('dashboard.savedsearches.description'),
        identifier: 'org-visallo-web-saved-search',
        componentPath: 'search/dashboard/savedSearch',
        configurationPath: 'search/dashboard/configure',
        grid: {
            width: 4,
            height: 4
        }
    });

    registry.registerExtension('org.visallo.dashboard.item', {
        title: i18n('dashboard.notifications.title'),
        description: i18n('dashboard.notifications.description'),
        identifier: 'org-visallo-web-notifications',
        componentPath: 'notifications/dashboardItem',
        grid: {
            width: 3,
            height: 3
        }
    });

    registry.registerExtension('org.visallo.dashboard.toolbar.item', {
        identifier: 'org-visallo-notification-clear-all',
        canHandle: function(options) {
            return options.extension.identifier === 'org-visallo-web-notifications'
        },
        tooltip: i18n('dashboard.notifications.clearall.hover'),
        icon: 'img/trash.png',
        action: {
            type: 'event',
            name: 'notificationClearAll'
        }
    });

    registry.registerExtension('org.visallo.dashboard.item', {
        title: i18n('dashboard.pie.entity.title'),
        description: i18n('dashboard.pie.entity.description'),
        identifier: 'org-visallo-web-dashboard-concept-counts',
        report: {
            defaultRenderer: 'org-visallo-pie',
            endpoint: '/vertex/search',
            endpointParameters: {
                q: '*',
                size: 0,
                filter: '[]',
                aggregations: [
                    {
                        type: 'term',
                        name: 'field',
                        field: 'http://visallo.org#conceptType'
                    }
                ].map(JSON.stringify)
            }
        },
        grid: {
            width: 4,
            height: 2
        }
    });

    registry.registerExtension('org.visallo.dashboard.item', {
        title: i18n('dashboard.pie.edge.title'),
        description: i18n('dashboard.pie.edge.description'),
        identifier: 'org-visallo-web-dashboard-edge-counts',
        report: {
            defaultRenderer: 'org-visallo-pie',
            endpoint: '/edge/search',
            endpointParameters: {
                q: '*',
                size: 0,
                filter: '[]',
                aggregations: [
                    {
                        type: 'term',
                        name: 'field',
                        field: '__edgeLabel'
                    }
                ].map(JSON.stringify)
            }
        },
        grid: {
            width: 4,
            height: 2
        }
    });

    registry.registerExtension('org.visallo.dashboard.item', {
        title: i18n('dashboard.welcome.title'),
        description: i18n('dashboard.welcome.description'),
        identifier: 'org-visallo-web-dashboard-welcome',
        componentPath: 'dashboard/items/welcome/welcome',
        options: {
            preventDefaultConfig: true
        },
        grid: {
            width: 5,
            height: 4
        }
    });
})
