/**
 * Allows a user to select an ontology property from a searchable dropdown component.
 *
 * @module components/PropertySelect
 * @flight Dropdown selection component for selecting properties from the ontology
 * @attr {Array.<object>=} properties The ontology properties to populate the list with, if not provided will use visible properties
 * @attr {string} [placeholder=Select Property] the placeholder text to display
 * @attr {boolean} [creatable=true] Allow creation of new properties if the user has ONTOLOGY_ADD privilege
 * @attr {boolean} [limitParentConceptId=''] Only show properties that are attached to this concept or it's descendants
 * @attr {boolean} [onlySearchable=false] Only show properties that have searchable attribute equal to true in ontology
 * @attr {boolean} [onlySortable=false] Only show properties that have sortable attribute equal to true in ontology
 * @attr {string} [onlyDataTypes=[]] Only show properties that have matching data type in ontology
 * @attr {string} [domainType] `concept` or `relationship`, show only properties that are attached to concepts or relationships
 * @attr {boolean} [hideCompound=false] Hide all compound/parent fields
 * @attr {boolean} [rollupCompound=true] Hide all dependent properties and only show the compound/parent fields
 * @attr {boolean} [focus=false] Activate the field for focus when finished rendering
 * @attr {string} [selectedProperty=''] Default the selection to this property IRI
 * @fires module:components/PropertySelect#propertyselected
 * @listens module:components/PropertySelect#filterProperties
 * @listens module:components/PropertySelect#selectProperty
 * @example
 * dataRequest('ontology', 'properties').then(function(properties) {
 *     PropertySelect.attachTo(node, {
 *         properties: properties
 *     })
 * })
 */
define([
    'flight/lib/component',
    'util/component/attacher'
], function(defineComponent, attacher) {


    var HIDE_PROPERTIES = ['http://visallo.org/comment#entry'];

    return defineComponent(PropertySelect);

    function PropertySelect() {
        this.after('teardown', function() {
            this.attacher.teardown();
        });

        this.after('initialize', function() {
            if ('unsupportedProperties' in this.attr) {
                console.warn('Attribute `unsupportedProperties` no longer used. Use filter attributes to customize list');
            }
            if ('maxItems' in this.attr) {
                console.warn('maxItems is no longer supported');
            }

            /**
             * Trigger to change the filters or list of properties the component works with.
             *
             * @event module:components/PropertySelect#filterProperties
             * @property {module:components/PropertySelect#filters} filters Attributes used to filter the properties in the component
             * @example
             * PropertySelect.attachTo($node)
             * //...
             * $node.trigger('filterProperties', { properties: newList })
             * $node.trigger('filterProperties', { conceptId: null }) //clear a filter
             * $node.trigger('filterProperties', { searchable: true, sortable: true, dataType: null }) //set multiple at once
             */
            this.on('filterProperties', function(event, filter) {
                const params = this.attacher._params;
                const nextFilter = _.omit({ ...params.filter, ...filter }, value => value === null);

                this.attacher.params({ ...params, filter: nextFilter }).attach();
            });

            /**
             * Trigger to change the the selected property (or clear it.)
             *
             * @event module:components/PropertySelect#selectProperty
             * @property {object} data
             * @property {string} data.property The property iri to select
             * @example
             * PropertySelect.attachTo($node)
             * //...
             * $node.trigger('selectProperty', { property: 'http://visallo.org#title' })
             * $node.trigger('selectProperty') // Clear
             */
            this.on('selectProperty', function(event, data) {
                const params = this.attacher._params;
                this.attacher.params({
                    ...params,
                    value: data && data.property || null
                }).attach();
            });

            const {
                filter = {},
                rollupCompound = true,
                hideCompound = false,
                focus,
                placeholder,
                properties,
                onlySearchable,
                onlySortable,
                onlyDataTypes,
                domainType,
                showAdminProperties,
                limitParentConceptId
            } = this.attr;

            if (_.isArray(onlyDataTypes)) {
                filter.dataTypes = onlyDataTypes
            }
            if (domainType) {
                filter.domainType = domainType;
            }
            if (onlySearchable === true) {
                filter.searchable = true;
            }
            if (showAdminProperties === true) {
                filter.userVisible = null;
            }
            if (onlySortable === true) {
                filter.sortable = true
            }
            if (limitParentConceptId) {
                filter.conceptId = limitParentConceptId;
            }
            if (properties) {
                filter.properties = _.indexBy(properties, 'title');
            }

            this.attacher = attacher()
                .node(this.node)
                .params({
                    properties: this.attr.properties,
                    filter: {
                        ...filter,
                        rollupCompound,
                        hideCompound
                    },
                    value: this.attr.selectedProperty,
                    autofocus: focus === true,
                    creatable: this.attr.creatable !== false,
                    placeholder
                })
                .behavior({
                    onSelected: (attacher, property) => {
                        /**
                         * When the user selects a property, this event will be
                         * triggered
                         *
                         * @event module:components/PropertySelect#propertyselected
                         * @property {object} data
                         * @property {object} data.property The property object that was selected
                         * @example
                         * $node.on('propertyselected', function(event, data) {
                         *     console.log(data.property)
                         * })
                         * PropertySelect.attachTo($node)
                         */
                        this.trigger('propertyselected', { property: property });
                    }
                })
                .path('components/ontology/PropertySelector');

            this.attacher.attach();
        });
    }
});
