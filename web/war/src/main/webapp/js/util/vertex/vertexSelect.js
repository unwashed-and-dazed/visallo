define([
    'flight/lib/component',
    'util/component/attacher'
], function(
    defineComponent,
    attacher) {
    'use strict';

    return defineComponent(VertexSelector);

    function VertexSelector() {

        this.before('teardown', function() {
            this._attacher.teardown();
            this._attacher = null;
        });

        this.after('initialize', function() {
            const {
                allowNew: creatable,
                defaultText,
                defaultText: placeholder,
                filterResultsToTitleField,
                focus,
                value = ''
            } = this.attr;

            this._attacher = attacher()
                .node(this.node)
                .path('components/element/ElementSelector')
                .params({
                    autofocus: focus === true,
                    creatable,
                    defaultText,
                    filterResultsToTitleField,
                    placeholder,
                    value
                })
                .behavior({
                    onCreateNewElement: (attacher, sign) => {
                         this.trigger('vertexSelected', { sign })
                    },
                    onElementSelected: (attacher, element) => {
                         this.trigger('vertexSelected', { vertex: element })
                    }
                });

            this._attacher.attach()
        });

    }
});
