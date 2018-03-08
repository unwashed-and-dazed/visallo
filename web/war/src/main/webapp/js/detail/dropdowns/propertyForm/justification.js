
define([
    'flight/lib/component',
    'util/component/attacher',
    'components/justification/JustificationEditor'
], function(
    defineComponent,
    Attacher,
    JustificationEditor
) {
    'use strict';

    return defineComponent(Justification);

    function Justification() {

        this.after('teardown', function() {
            this.attacher.teardown();
        })

        this.after('initialize', function() {

            this.on('valuepasted', function(event, {value}) {
                this.attacher.params({
                    ...this.attacher._params,
                    pastedValue: value
                }).attach();
            });

            const { justificationOverride, pastedValue, justificationText, sourceInfo } = this.attr;
            const params = {};

            if (justificationOverride) {
                params.validationOverride = justificationOverride;
            }
            if (pastedValue) {
                params.pastedValue = pastedValue;
            }
            if (sourceInfo) {
                params.value = { sourceInfo }
            } else if (justificationText) {
                params.value = { justificationText }
            }

            this.attacher = Attacher()
                .component(JustificationEditor)
                .params(params)
                .behavior({
                    onJustificationChanged: (attacher, justification) => {
                        const params = attacher._params;
                        const { valid, value } = justification;
                        attacher.params({ ...params, value }).attach();
                        this.trigger('justificationchange', { valid, ...value });
                    }
                })
                .node(this.node)

            this.attacher.attach();
        });
    }
});
