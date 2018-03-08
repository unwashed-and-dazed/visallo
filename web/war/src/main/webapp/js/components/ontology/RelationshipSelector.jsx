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

    const filterList = (conceptDescendents, relationships, relationshipKeys, filter) => relationships.filter(r => {
        const domainRanges = _.flatten(relationshipKeys.map(k => r[k]));
        return _.any(domainRanges, iri => {
            return (
                filter === iri ||
                (conceptDescendents[iri] && conceptDescendents[iri].includes(filter))
            );
        });
    });
    const RelationshipSelector = createReactClass({
        propTypes: {
            conceptDescendents: PropTypes.object.isRequired,
            relationships: PropTypes.array.isRequired,
            filter: PropTypes.shape({
                sourceId: PropTypes.string,
                targetId: PropTypes.string,
                conceptId: PropTypes.string,
                relationshipId: PropTypes.string
            }),
            placeholder: PropTypes.string
        },
        getDefaultProps() {
            return { creatable: true, placeholder: i18n('relationship.field.placeholder') }
        },
        render() {
            const {
                conceptDescendents,
                relationshipAncestors,
                privileges,
                relationships,
                filter,
                creatable,
                ...rest
            } = this.props;
            const formProps = { ...filter };

            var options = relationships;

            if (filter) {
                const { conceptId, sourceId, targetId, relationshipId } = filter;
                if (conceptId && (sourceId || targetId)) {
                    throw new Error('only one of conceptId or source/target can be sent');
                }
                if (relationshipId) {
                    options = options.filter(o => o.title === relationshipId || relationshipAncestors[relationshipId].includes(o.title));
                }
                if (conceptId) {
                    options = filterList(conceptDescendents, options, ['domainConceptIris', 'rangeConceptIris'], conceptId);
                } else {
                    if (sourceId) {
                        options = filterList(conceptDescendents, options, ['domainConceptIris'], sourceId);
                    }
                    if (targetId) {
                        options = filterList(conceptDescendents, options, ['rangeConceptIris'], targetId);
                    }
                }
            }

            return (
                <BaseSelect
                    createForm={'components/ontology/RelationshipForm'}
                    formProps={formProps}
                    options={options}
                    creatable={creatable && Boolean(privileges.ONTOLOGY_ADD)}
                    {...rest} />
            );
        }
    });

    return redux.connect(
        (state, props) => {
            return {
                privileges: userSelectors.getPrivileges(state),
                conceptDescendents: ontologySelectors.getConceptDescendents(state),
                relationshipAncestors: ontologySelectors.getRelationshipAncestors(state),
                relationships: ontologySelectors.getVisibleRelationships(state),
                iriKeys: ontologySelectors.getRelationshipKeyIris(state),
                ...props
            };
        },

        (dispatch, props) => ({
            onCreate: (relationship, options) => {
                dispatch(ontologyActions.addRelationship(relationship, options));
            }
        })
    )(RelationshipSelector);
});

