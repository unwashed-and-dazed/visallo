define([
    'flight/lib/component',
    'util/popovers/withPopover',
    'util/component/attacher',
    'util/range'
], function(
    defineComponent,
    withPopover,
    Attacher,
    rangeUtils) {

    return defineComponent(TextPopover, withPopover);

    function TextPopover() {

        this.before('teardown', function() {
            if (this.attacher) {
                this.attacher.teardown();
                this.attacher = null;
            }
        })

        this.before('initialize', function(node, config) {
            config.hideDialog = true;
            config.template = '/detail/text/popover/popover.hbs';
            this.after('setupWithTemplate', () => {
                const {
                    selection,
                    terms,
                    sourceVertexId,
                    targetVertexId,
                    artifactId,
                    propertyName,
                    propertyKey
                } = this.attr;

                this.attacher = Attacher()
                    .node(this.popover.find('.popover-content'))
                    .path('detail/text/popover/TermContainer')
                    .params({ selection, terms, artifactId, propertyName, propertyKey, sourceVertexId, targetVertexId })
                    .behavior({
                        onHoverTerm: (attacher, id) => {
                            this.trigger('hoverTerm', { id });
                        },
                        comment: (attacher, sourceInfo) => {
                            this.trigger('commentOnSelection', sourceInfo);
                        },
                        openFullscreen: (attacher, elementIds) => {
                            this.trigger(document, 'openFullscreen', elementIds);
                        },
                        reloadText: () => {
                            this.trigger(document, 'textUpdated', { vertexId: artifactId });
                        },
                        closeDialog: () => {
                            rangeUtils.clearSelection();
                            this.teardown();
                        }
                    });

                this.attacher.attach().then(() => {
                    this.dialog.show();
                    this.positionDialog();
                })
            })
        })
    }
});
