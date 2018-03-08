define([
    './controlDragPopover',
    './findPath/findPathPopoverShim',
    './createConnectionPopover',
    './controlDragPopoverTpl',
    './createConnectionPopoverTpl',
    './findPath/findPathPopoverTpl'
], function(ControlDrag, FindPath, CreateConnection) {

    return function(connectionType) {
        return connectionType === 'CreateConnection' ?
            CreateConnection :
            connectionType === 'FindPath' ?
            FindPath :
            ControlDrag
    }
})
