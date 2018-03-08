define([
    'create-react-class',
    'prop-types',
    'classnames',
    'components/element/Element'
], function(createReactClass, PropTypes, classNames, Element) {

    const Unresolve = createReactClass({
        render() {
            const { error, onCancel, loading, conceptType, getConceptOrDefault, vertices, resolvedToVertexId, title } = this.props;

            const concept = getConceptOrDefault(conceptType);
            const element = vertices[resolvedToVertexId];

            return (
                <div className="form">
                    { error ? (<Alert error={error} />) : null }

                    <h1>{i18n('detail.text.terms.form.unresolve')}</h1>

                    <p>{i18n('detail.text.terms.form.unresolve.p')} <em>{concept.displayName}</em>, <Element element={element} />?</p>
                    <p style={{fontStyle: 'italic', color: '#999', fontSize: '90%'}}>{i18n('detail.text.terms.form.unresolve.note')}</p>

                    <div className="buttons">
                        <button onClick={onCancel} className="btn btn-link btn-small">{i18n('detail.text.terms.form.cancel')}</button>
                        <button onClick={this.onUnresolve} className={classNames('btn btn-danger btn-small', { loading })}>{i18n('detail.text.terms.form.unresolve.button')}</button>
                    </div>
                </div>
            )
        },
        onUnresolve() {
            this.props.onUnresolve(this.props.id);
        }
    });

    return Unresolve;
});
