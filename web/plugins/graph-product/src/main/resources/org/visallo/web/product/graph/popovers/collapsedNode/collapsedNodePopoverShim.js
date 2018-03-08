define([
    'flight/lib/component',
    '../withVertexPopover',
    'util/component/attacher',
], function(defineComponent, withVertexPopover, attacher) {
    'use strict';

    return defineComponent(CollapsedNodePopoverShim, withVertexPopover);

    function CollapsedNodePopoverShim() {
        this.popoverInitialize = function() {
            this.attacher = attacher()
                .node(this.popover.find('.popover-content'))
                .path('org/visallo/web/product/graph/dist/CollapsedNodePopoverConfig')
                .params(_.extend(this.attr.props, {
                    close: () => this.teardown()
                }));

            this.attacher.attach()
                .then(() => {
                    this.before('teardown', function() {
                        this.attacher.teardown();
                    });

                    this.positionDialog();
                });
        };

        this.getTemplate = function() {
            return new Promise(
                f => require(['org/visallo/web/product/graph/popovers/collapsedNode/collapsedNodePopoverTpl.hbs'], f)
            );
        };
    }
});
