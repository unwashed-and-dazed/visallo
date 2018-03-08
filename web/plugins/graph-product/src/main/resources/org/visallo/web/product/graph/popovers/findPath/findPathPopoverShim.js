define([
    'flight/lib/component',
    '../withVertexPopover',
    'util/component/attacher'
], function(defineComponent,
            withVertexPopover,
            attacher) {
    'use strict';

    return defineComponent(FindPathPopoverShim, withVertexPopover);

    function FindPathPopoverShim() {
        this.before('teardown', function() {
            this.attacher.teardown();
            this.trigger('finishedVertexConnection');
        });

        this.getTemplate = function() {
            return new Promise(f => require(['./findPathPopoverTpl'], f));
        };

        this.popoverInitialize = function() {
            var self = this;

            this.trigger('defocusPaths');
            this.$findPathWrapper = this.$node.find('.popover-content');

            this.attacher = attacher()
                .node(this.$findPathWrapper)
                .path('org/visallo/web/product/graph/dist/FindPathPopoverContainer')
                .params({
                    outVertexId: this.attr.outVertexId,
                    inVertexId: this.attr.inVertexId,
                    success: function() {
                        self.teardown();
                        self.trigger('showActivityDisplay');
                    }
                });

            this.attacher.attach()
                .then(() => {
                    this.positionDialog();
                });
        };
    }
});
