/**
 * Allows a user to select an ontology concept from a searchable dropdown component.
 *
 * @module components/ConceptSelect
 * @flight Dropdown selection component for selecting concepts from the ontology
 * @attr {string} [defaultText=Choose a Concept...] the placeholder text to display
 * @attr {boolean} [showAdminConcepts=false] Whether concepts that aren't user visible should be displayed
 * @attr {boolean} [onlySearchable=false] Only show concepts that have searchable attribute equal to true in ontology
 * @attr {boolean} [creatable=true] Allow creation of new concepts if the user has ONTOLOGY_ADD privilege
 * @attr {string} [restrictConcept=''] Only allow selection of this concept or its descendants
 * @attr {string} [limitRelatedToConceptId=''] Only allow selection of concepts where there is a valid edge containing the passed in concept IRI
 * @attr {string} [selectedConceptId=''] Default the selection to this concept IRI
 * @attr {string} [selectedConceptIntent=''] Default the selection to this the first concept with this intent defined in ontology
 * @attr {boolean} [focus=false] Activate the field for focus when finished rendering
 * @fires module:components/ConceptSelect#conceptSelected
 * @listens module:components/ConceptSelect#clearSelectedConcept
 * @listens module:components/ConceptSelect#selectConceptId
 * @listens module:components/ConceptSelect#enableConcept
 * @example <caption>Use default component</caption>
 * ConceptSelect.attachTo(node)
 * @example <caption>Select a concept</caption>
 * ConceptSelect.attachTo(node, {
 *     selectedConceptId: 'http://www.visallo.org/minimal#person'
 * })
 */
define([
    'flight/lib/component',
    'util/component/attacher'
], function(defineComponent, attacher) {

    return defineComponent(ConceptSelector);

    function ConceptSelector() {
        this.after('teardown', function() {
            this.attacher.teardown();
        })

        this.after('initialize', function() {

            /**
             * Clears the selected concept from the component. Will not fire
             * conceptSelected.
             *
             * @event module:components/ConceptSelect#clearSelectedConcept
             * @example
             * ConceptSelect.attachTo($node)
             * //...
             * $node.trigger('clearSelectedConcept')
             */
            this.on('clearSelectedConcept', function(event) {
                this.attacher.params({ ...this.attacher._params, value: null }).attach();
            })

            /**
             * Set the selected concept. Will not fire conceptSelected.
             *
             * If no conceptId is passed or it's empty it'll clear the
             * selection.
             *
             * @event module:components/ConceptSelect#selectConceptId
             * @property {object} data
             * @property {string} [data.conceptId=''] The concept IRI to select
             * @example
             * ConceptSelect.attachTo($node)
             * //...
             * $node.trigger('selectConceptId', {
             *     conceptId: 'http://www.visallo.org/minimal#person'
             * })
             */
            this.on('selectConceptId', function(event, { conceptId }) {
                this.attacher.params({ ...this.attacher._params, value: conceptId }).attach();
            })

            /**
             * Enable / Disable the component. Only pass one property (enable
             * or disable)
             *
             * @event module:components/ConceptSelect#enableConcept
             * @property {object} data
             * @property {boolean} data.enable Enable this component and allow user entry
             * @property {boolean} data.disable Disable this component from user entry
             * @example <caption>Disable</caption>
             * ConceptSelect.attachTo($node)
             * //...
             * $node.trigger('enableConcept', { disable: true })
             * @example <caption>Enable</caption>
             * ConceptSelect.attachTo($node)
             * //...
             * $node.trigger('enableConcept', { enable: true })
             */
            this.on('enableConcept', function(event, { disable, enable }) {
                const disabled = disable === true || enable === false
                this.attacher.params({ ...this.attacher._params, disabled }).attach();
            })

            const filter = {};
            if (this.attr.showAdminConcepts === true) {
                filter.userVisible = null;
            }
            if (this.attr.onlySearchable === true) {
                filter.searchable = true;
            }
            if (this.attr.restrictConcept) {
                filter.conceptId = this.attr.restrictConcept;
            }
            if (this.attr.limitRelatedToConceptId) {
                filter.relatedToConceptId = this.attr.limitRelatedToConceptId;
            }
            if ('maxItems' in this.attr) {
                console.warn('maxItems is no longer supported');
            }
            if ('selectedConceptIntent' in this.attr) {
                console.warn('selectedConceptIntent is no longer supported');
            }

            this.attacher = attacher()
                .node(this.node)
                .params({
                    filter,
                    creatable: this.attr.creatable !== false,
                    value: this.attr.selectedConceptId,
                    placeholder: this.attr.defaultText,
                    autofocus: this.attr.focus === true
                })
                .behavior({
                    onSelected: (attacher, concept) => {
                        /**
                         * Triggered when the user selects a concept from the list.
                         *
                         * @event module:components/ConceptSelect#conceptSelected
                         * @property {object} data
                         * @property {object} data.concept The concept object that was selected
                         * @example
                         * $node.on('conceptSelected', function(event, data) {
                         *     console.log(data.concept)
                         * })
                         * ConceptSelect.attachTo($node)
                         */
                        this.trigger('conceptSelected', { concept })
                    }
                })
                .path('components/ontology/ConceptSelector')

            this.attacher.attach();
        })
    }
});
