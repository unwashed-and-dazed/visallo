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

    const ConceptsSelector = createReactClass({
        propTypes: {
            filter: PropTypes.shape({
                conceptId: PropTypes.string,
                relatedToConceptId: PropTypes.string,
                showAncestors: PropTypes.bool,
                userVisible: PropTypes.bool,
                searchable: PropTypes.bool
            }),
            conceptAncestors: PropTypes.object.isRequired,
            concepts: PropTypes.array.isRequired,
            privileges: PropTypes.object.isRequired,
            placeholder: PropTypes.string,
            value: PropTypes.string
        },
        getDefaultProps() {
            return { creatable: true, placeholder: i18n('concept.field.placeholder') }
        },
        render() {
            const {
                conceptAncestors,
                concepts,
                conceptsToConcepts,
                filter,
                privileges,
                creatable,
                ...rest
            } = this.props;

            var options = concepts;
            if (filter) {
                options = concepts.filter(o => {
                    return (
                        filter.conceptId ?
                            (o.id === filter.conceptId ||
                            (!filter.showAncestors || conceptAncestors[filter.conceptId].includes(o.id))) : true
                    ) && (
                        filter.userVisible === undefined || filter.userVisible === true ?
                            o.userVisible !== false : true
                    ) && (
                        filter.searchable === true ?
                            o.searchable !== false : true
                    ) && (
                        filter.relatedToConceptId ?
                            (
                                conceptsToConcepts[filter.relatedToConceptId] &&
                                conceptsToConcepts[filter.relatedToConceptId].includes(o.id)
                            ) : true
                    );
                })
            }
            return (
                <BaseSelect
                    createForm={'components/ontology/ConceptForm'}
                    options={options}
                    creatable={creatable && Boolean(privileges.ONTOLOGY_ADD)}
                    {...rest} />
            );
        }
    });

    return redux.connect(
        (state, props) => {
            var otherFilters = props.filter;
            var concepts = ontologySelectors.getVisibleConceptsList(state);
            var conceptsToConcepts;
            var depthKey = 'depth';
            var pathKey = 'path';

            if (otherFilters) {
                const { userVisible, ...rest } = otherFilters;
                otherFilters = rest;
                const showAdmin = userVisible === null;
                if (showAdmin) {
                    concepts = ontologySelectors.getConceptsList(state);
                    depthKey = 'fullDepth';
                    pathKey = 'fullPath';
                }
                if (otherFilters.relatedToConceptId) {
                    conceptsToConcepts = ontologySelectors.getConceptsByRelatedConcept(state);
                }
            }
            return {
                privileges: userSelectors.getPrivileges(state),
                concepts,
                conceptAncestors: ontologySelectors.getConceptAncestors(state),
                iriKeys: ontologySelectors.getConceptKeyIris(state),
                filter: otherFilters,
                conceptsToConcepts,
                depthKey,
                pathKey,
                ...props
            };
        },

        (dispatch, props) => ({
            onCreate: (concept, options) => {
                dispatch(ontologyActions.addConcept(concept, options));
            }
        })
    )(ConceptsSelector);
});
