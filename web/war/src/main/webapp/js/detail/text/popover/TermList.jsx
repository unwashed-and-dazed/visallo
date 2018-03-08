define([
    'create-react-class',
    'prop-types',
    './Term',
    'components/Alert'
], function(createReactClass, PropTypes, Term, Alert) {

    const TermList = createReactClass({
        propTypes: {
            terms: PropTypes.array.isRequired
        },
        render() {
            const { selection, terms, ...rest } = this.props;

            const transformed = terms.map(term => {
                const { termMentionFor, resolvedToEdgeId, resolvedToVertexId } = term;
                const resolved = resolvedToVertexId && resolvedToEdgeId;
                let type;
                if (resolved) type = 'resolved';
                else if (termMentionFor) type = 'justification';
                else type = 'suggestion';
                return { ...term, type };
            })
            const order = ['resolved', 'suggestion', 'justification'];
            const sorted = _.sortBy(transformed, ({ type }) => order.indexOf(type));

            if (selection) {
                sorted.splice(0, 0, {
                    ...selection,
                    type: 'selection',
                    id: 'selection',
                    refId: null
                })
            }

            if (sorted.length) {
                return (
                    <ul onMouseLeave={this.onMouseLeave}>
                        { sorted.map(term => <Term key={term.id} onHoverTerm={this.onHoverTerm} term={term} {...rest} />) }
                    </ul>
                )
            }

            return <Alert error={i18n('detail.text.terms.list.error')}/>
        },
        onHoverTerm(id) {
            this.props.onHoverTerm(id);
        },
        onMouseLeave() {
            this.props.onHoverTerm();
        }
    });

    return TermList;
});
