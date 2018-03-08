/**
 * Renders the json justification object. Could be either justification text or
 * full source info with link to source.
 *
 * One of `justificationMetadata` or `sourceMetadata` must be provided.
 * If both justificationMetadata and sourceMetadata are given, only justificationMetadata is used.
 *
 * @module components/JustificationViewer
 * @flight Displays justification information
 * @attr {boolean} [linkToSource=true] Show the source link if available
 * @attr {object} [justificationMetadata]
 * @attr {string} justificationMetadata.justificationText The text to display
 * @attr {object} [sourceMetadata]
 * @attr {string} sourceMetadata.snippet The snippet from source material to display
 * @attr {string} sourceMetadata.textPropertyKey The property key of the text property in source
 * @attr {string} sourceMetadata.textPropertyName The property name of the text property in source
 * @attr {string} sourceMetadata.startOffset The character start index of snippet in source
 * @attr {string} sourceMetadata.endOffset The character end index of snippet in source
 * @attr {string} sourceMetadata.vertexId The vertexId of the source
 * @example <caption>Text</caption>
 * JustificationViewer.attachTo(node, {
 *     justificationMetadata: {
 *         justificationText: 'Justification for property here'
 *     }
 * })
 * @example <caption>Source Reference</caption>
 * JustificationViewer.attachTo(node, {
 *     sourceMetadata: {
 *         snippet: '[html snippet]',
 *         vertexId: vertexId,
 *         textPropertyKey: textPropertyKey,
 *         textPropertyName: textPropertyName,
 *         startOffset: 0,
 *         endOffset: 42
 *     }
 * })
 */
define([
    'flight/lib/component',
    'util/component/attacher',
    'components/justification/JustificationViewer'
], function(defineComponent, Attacher, JustificationViewerReact) {

    return defineComponent(JustificationViewer);

    function JustificationViewer() {

        this.before('teardown', function() {
            if (this.attacher) {
                this.attacher.teardown();
                this.attacher = null;
            }
        })

        this.after('initialize', function() {

            const { linkToSource = true, sourceMetadata: sourceInfo, justificationMetadata } = this.attr;
            const params = {
                linkToSource,
                value: {}
            }

            if (sourceInfo) {
                params.value.sourceInfo = sourceInfo;
            } else if (justificationMetadata) {
                params.value.justificationText = justificationMetadata.justificationText;
            }

            this.attacher = Attacher()
                .component(JustificationViewerReact)
                .node(this.node)
                .params(params)
            this.attacher.attach();
        });

    }
});
