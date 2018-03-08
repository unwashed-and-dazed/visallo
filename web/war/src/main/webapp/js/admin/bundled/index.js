define([
    'configuration/plugins/registry'
], function(registry) {
    'use strict';

    var adminExtensionPoint = 'org.visallo.admin';

    registry.registerExtension(adminExtensionPoint, {
        componentPath: 'admin/bundled/uiExtensionList/index',
        section: i18n('admin.plugin.title'),
        name: i18n('admin.plugin.uiExtensions'),
        subtitle: i18n('admin.plugin.uiExtensions.subtitle')
    });

    registry.registerExtension(adminExtensionPoint, {
        componentPath: 'admin/bundled/pluginList/PluginList',
        section: i18n('admin.plugin.title'),
        name: i18n('admin.plugin.list'),
        subtitle: i18n('admin.plugin.list.subtitle')
    });

    registry.registerExtension(adminExtensionPoint, {
        componentPath: 'admin/bundled/notifications/list',
        section: i18n('admin.notifications.title'),
        name: i18n('admin.notifications.list'),
        subtitle: i18n('admin.notifications.list.subtitle')
    });

    registry.registerExtension(adminExtensionPoint, {
        componentPath: 'admin/bundled/notifications/create',
        section: i18n('admin.notifications.title'),
        name: i18n('admin.notifications.create'),
        subtitle: i18n('admin.notifications.create.subtitle')
    });
})
