define([
    'create-react-class',
    'prop-types',
    'react-redux',
    './BaseSelect',
    'data/web-worker/store/user/selectors',
    'data/web-worker/store/ontology/selectors',
    'data/web-worker/store/ontology/actions'
], function(
    createReactClass,
    PropTypes,
    redux,
    BaseSelect,
    userSelectors,
    ontologySelectors,
    ontologyActions) {

    const FilterProps = ['dataType', 'deleteable', 'searchable', 'sortable', 'updateable', 'userVisible', 'addable'];
    const FilterPropDefaults = {
        userVisible: true
    };

    const PropertySelector = createReactClass({
        propTypes: {
            /**
             * Property select list filters
             *
             * @typedef {object} module:components/PropertySelect#filters
             *
             * @property {Array.<object>} properties Override the array of properties to use, any other filters defined will still be applied to this array
             * @property {string|Array.<string>} [conceptId] Filter to only properties attached to this concept, or any of the concepts if given an array
             * @property {string|Array.<string>} [relationshipId] Filter to only properties attached to this relationship, or any of the relationships if given an array
             * @property {boolean} [hideCompound] Exclude compound properties
             * @property {string} [dataType] Include properties of only this data type
             * @property {Array.<string>} [dataTypes] Include properties of only these data types
             * @property {string} [domainType] `concept` or `relationship`, include properties that are attached to an element type
             * @property {boolean} [addable] Include properties whose `addable` value matches the value provided, if `addable` is not defined on the property it will return true
             * @property {boolean} [userVisible=true] Include properties whose `userVisible` value matches the value provided, if `userVisible` is not defined on the property it will return true
             * @property {boolean} [searchable] Include properties whose `searchable` value matches the value provided, if `searchable` is not defined on the property it will return true
             * @property {boolean} [deleteable] Include properties whose `deleteable` value matches the value provided, if `deleteable` is not defined on the property it will return true
             * @property {boolean} [sortable] Include properties whose `sortable` value matches the value provided, if `sortable` is not defined on the property it will return true
             * @property {boolean} [updateable] Include properties whose `updateable` value matches the value provided, if `updateable` is not defined on the property it will return true
             *
             */
            filter: PropTypes.shape({
                properties: PropTypes.Array,
                conceptId: PropTypes.oneOfType([PropTypes.string, PropTypes.array]),
                relationshipId: PropTypes.oneOfType([PropTypes.string, PropTypes.array]),
                hideCompound: PropTypes.bool,
                dataType: PropTypes.string,
                dataTypes: PropTypes.array,
                domainType: PropTypes.string,
                addable: PropTypes.bool,
                userVisible: PropTypes.bool,
                searchable: PropTypes.bool,
                deleteable: PropTypes.bool,
                sortable: PropTypes.bool,
                updateable: PropTypes.bool
            }),
            value: PropTypes.string,
            properties: PropTypes.array.isRequired,
            propertiesByConcept: PropTypes.object.isRequired,
            propertiesByRelationship: PropTypes.object.isRequired,
            conceptProperties: PropTypes.object.isRequired,
            relationshipProperties: PropTypes.object.isRequired,
            privileges: PropTypes.object.isRequired,
            placeholder: PropTypes.string
        },
        getDefaultProps() {
            return { creatable: true, placeholder: i18n('property.field.placeholder') }
        },
        render() {
            const {
                properties,
                conceptProperties,
                relationshipProperties,
                propertiesByConcept,
                propertiesByRelationship,
                filter,
                privileges, creatable, ...rest } = this.props;
            const formProps = { ...(filter || {}) };
            const dependentPropertyIris = [];
            const filterByDomainType = filter.domainType && !filter.conceptId && !filter.relationshipId;
            let options = properties.filter((p, i, list) => {
                if (p.header) {
                    return true;
                }

                let test = true;
                if (filter && filter.properties) {
                    test = test && p.title in filter.properties;
                }

                if (test && filter && _.isArray(filter.dataTypes) && filter.dataTypes.length) {
                    test = test && filter.dataTypes.includes(p.dataType)
                }

                if (test && filter && filterByDomainType) {
                    let domainTypeProperties;
                    if (filterByDomainType) {
                        if (filter.domainType === 'concept') {
                            domainTypeProperties = conceptProperties;
                        } else if (filter.domainType === 'relationship') {
                            domainTypeProperties = relationshipProperties;
                        }
                    }
                    test = test && ((!filterByDomainType || domainTypeProperties[p.title]) || p.title.startsWith('dataType:'));
                }

                if (test && filter && filter.conceptId) {
                    const conceptIris = _.isArray(filter.conceptId) ? filter.conceptId : [ filter.conceptId ];
                    const belongsToConcepts = conceptIris.some(concept => {
                        const conceptProps = propertiesByConcept[concept];
                        return conceptProps && conceptProps[p.title];
                    });

                    if (_.isString(filter.conceptId) || filter.conceptId.length === 1) {
                        formProps.domain = _.isArray(filter.conceptId) ? filter.conceptId[0] : filter.conceptId;
                    }

                    test = test && (belongsToConcepts || p.title.startsWith('dataType:'));
                }

                if (test && filter && filter.relationshipId) {
                    const relationshipIris = _.isArray(filter.relationshipId) ? filter.relationshipId : [ filter.relationshipId ];
                    const belongsToRelationships = relationshipIris.some(relationship => {
                        const relationshipProps = propertiesByRelationship[relationship];
                        return relationshipProps && relationshipProps[p.title];
                    });

                    if (_.isString(filter.relationshipId) || filter.relationshipId.length === 1) {
                        formProps.domain = _.isArray(filter.relationshipId) ? filter.relationshipId[0] : filter.relationshipId;
                    }

                    test = test && (belongsToRelationships || p.title.startsWith('dataType:'));

                }

                if (test && filter && filter.hideCompound) {
                    test = test && !p.dependentPropertyIris;
                }

                if (test && filter && filter.rollupCompound && p.dependentPropertyIris) {
                    dependentPropertyIris.push(...p.dependentPropertyIris);
                }

                if (test && !p.title.startsWith('dataType:')) {
                    FilterProps.forEach(fp => {
                        if (filter && fp in filter) {
                            // otherwise any value is valid
                            if (filter[fp] !== undefined && filter[fp] !== null) {
                                test = test && p[fp] === filter[fp];
                            }
                        }
                    })
                }
                return test;
            });

            if (filter && filter.rollupCompound) {
                const uniqueIris = _.object(dependentPropertyIris.map(iri => [iri, true]))
                options = options.filter(o => !uniqueIris[o.title]);
            }

            removeEmptyHeaders(options)

            let usesLegacyFilterProperties = false;
            if (creatable && filter && filter.properties) {
                console.warn('Creating properties when using old filter syntax (passing properties) is not supported');
                usesLegacyFilterProperties = true;
            }

            return (
                <BaseSelect
                    createForm={'components/ontology/PropertyForm'}
                    formProps={formProps}
                    options={options}
                    creatable={creatable && !usesLegacyFilterProperties && Boolean(privileges.ONTOLOGY_ADD)}
                    {...rest} />
            );
        }
    });

    return redux.connect(
        (state, props) => {
            let otherFilters = props.filter;
            const showAdmin = otherFilters && otherFilters.userVisible === null;

            let properties = props.properties || (showAdmin
                ? ontologySelectors.getPropertiesWithHeaders(state)
                : ontologySelectors.getVisiblePropertiesWithHeaders(state)
            );

            return {
                privileges: userSelectors.getPrivileges(state),
                propertiesByConcept: ontologySelectors.getPropertiesByConcept(state),
                propertiesByRelationship: ontologySelectors.getPropertiesByRelationship(state),
                conceptProperties: ontologySelectors.getConceptProperties(state),
                relationshipProperties: ontologySelectors.getRelationshipProperties(state),
                iriKeys: ontologySelectors.getPropertyKeyIris(state),
                ...props,
                properties
            };
        },

        (dispatch, props) => ({
            onCreate: ({ displayName, dataType, displayType, domain }, options) => {
                let property = {
                    displayName,
                    dataType,
                    displayType,
                    ...domain,
                };
                if (!property.displayType) {
                    delete property.displayType;
                }
                dispatch(ontologyActions.addProperty(property, options));
            }
        })
    )(PropertySelector);

    function removeEmptyHeaders(options) {
        const removeHeaderIndices = [];
        let lastHeaderIndex = -1;
        options.forEach((o, i, list) => {
            if (o.header) {
                if (i > 0 && lastHeaderIndex === (i - 1)) {
                    removeHeaderIndices.push(lastHeaderIndex)
                }
                if (i === (list.length - 1)) {
                    removeHeaderIndices.push(i)
                }
                lastHeaderIndex = i;
            }
        })
        removeHeaderIndices.reverse().forEach(i => {
            options.splice(i, 1);
        })
    }
});
