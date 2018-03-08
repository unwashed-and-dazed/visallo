define([
    'flight/lib/component',
    'util/component/attacher'
], function(defineComponent, attacher) {
    'use strict';

    return defineComponent(Menu);

    function Menu() {

        this.after('initialize', function() {
            var self = this;

            this.on(document, 'closeVertexMenu', this.teardown);

            this.$node.append('<div class="vertex-menu-wrapper"></div>');
            this.$menu = this.$node.find('.vertex-menu-wrapper');

            this.attacher = attacher()
                .node(this.$menu)
                .path('components/ElementContextMenu')
                .params({
                    domElement: this.attr.element || this.$menu.get(0),
                    edgeIds: this.attr.edgeIds,
                    vertexId: this.attr.vertexId,
                    collapsedItemId: this.attr.collapsedItemId,
                    position: this.attr.position
                });

                this.attacher.attach();

            _.defer(function() {
                $(document).off('.vertexMenu').on('click.vertexMenu', function() {
                    $(document).off('.vertexMenu');
                    _.defer(function() {
                        self.teardown();
                    })
                });
            });
        });

        this.before('teardown', function() {
            this.attacher.node(this.$menu).teardown();
        });

        this.after('teardown', function() {
            $(document).off('.vertexMenu');
        });

    }
});
