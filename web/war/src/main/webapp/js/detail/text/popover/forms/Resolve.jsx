define([
    'create-react-class',
    'prop-types',
    'classnames',
    'components/Alert',
    'components/element/ElementSelector',
    'components/ontology/ConceptSelector',
    'components/justification/JustificationEditor',
    'components/visibility/VisibilityEditor',
    'util/vertex/formatters'
], function(
    createReactClass,
    PropTypes,
    classNames,
    Alert,
    ElementSelector,
    ConceptSelector,
    JustificationEditor,
    VisibilityEditor,
    F) {

    const DefaultVisibility = { value: '', valid: true };
    const Resolve = createReactClass({
        propTypes: {
            artifactId: PropTypes.string.isRequired,
            propertyKey: PropTypes.string.isRequired,
            propertyName: PropTypes.string.isRequired,
            mentionEnd: PropTypes.number.isRequired,
            mentionStart: PropTypes.number.isRequired,
            sign: PropTypes.string.isRequired,
            onCancel: PropTypes.func.isRequired,
            onResolve: PropTypes.func.isRequired,
            error: PropTypes.instanceOf(Error),
            loading: PropTypes.bool,
            resolvedFromTermMention: PropTypes.string,
            conceptType: PropTypes.string
        },
        getInitialState() {
            return {
                visibility: DefaultVisibility,
                conceptId: this.props.conceptType
            }
        },
        render() {
            const { onCancel, sign, conceptType, error, loading = false, ...rest } = this.props;
            const { resolvedVertexId, newElementText, conceptId, justification = {}, visibility } = this.state;
            const { valid: justificationValid, value: justificationValues } = justification

            return (
                <div className="form">
                    { error ? (<Alert error={error} />) : null }

                    <h1>{ resolvedVertexId ? i18n('detail.text.terms.form.resolve.existing') :
                        newElementText ? i18n('detail.text.terms.form.resolve.new') : i18n('detail.text.terms.form.resolve.search')}</h1>
                    <ElementSelector
                        creatable
                        filterResultsToTitleField
                        searchOptions={{ matchType: 'vertex' }}
                        value={sign}
                        placeholder={i18n('detail.text.terms.form.resolve.placeholder')}
                        onElementSelected={this.onElementSelected}
                        onCreateNewElement={this.onCreateNewElement}
                        createNewRenderer={value => i18n('detail.text.terms.form.resolve.create', value)}
                        createNewValueRenderer={value => value } />
                    { (resolvedVertexId || newElementText) ? (
                        <div>
                            { !resolvedVertexId && newElementText ?
                                (<ConceptSelector
                                    clearable={conceptId !== conceptType}
                                    onSelected={this.onConceptSelected}
                                    value={conceptId || conceptType || ''} />) : null
                            }

                            <JustificationEditor
                                value={justificationValues}
                                onJustificationChanged={this.onJustificationChanged}
                                />

                            { !resolvedVertexId ?
                                (<VisibilityEditor
                                    value={visibility && visibility.value}
                                    onVisibilityChanged={this.onVisibilityChanged} />) : null
                            }
                        </div>
                    ) : null }
                    <div className="buttons">
                        <button onClick={onCancel} className="btn btn-link btn-small">{i18n('detail.text.terms.form.cancel')}</button>
                        <button
                            disabled={loading || !this.isValid()}
                            onClick={this.onResolve}
                            className={classNames('btn-success btn btn-small', {loading})}>{i18n('detail.text.terms.form.resolve.button')}</button>
                    </div>
                </div>
            )
        },
        onResolve() {
            const {
                artifactId, propertyName, propertyKey,
                resolvedFromTermMention, sign: initialSign,
                mentionStart, mentionEnd
            } = this.props;

            const {
                resolvedVertexId, newElementText: sign,
                conceptId, justification, visibility
            } = this.state;

            this.props.onResolve({
                visibilitySource: visibility.value,
                artifactId,
                propertyName,
                propertyKey,
                resolvedFromTermMention,
                mentionStart,
                mentionEnd,
                ...justification.value,
                ...(resolvedVertexId ? { resolvedVertexId, sign: initialSign } : { sign, conceptId })
            })
        },
        onElementSelected(element) {
            this.setState({
                resolvedVertexId: element ? element.id : null,
                newElementText: null,
                visibility: DefaultVisibility
            })
        },
        onCreateNewElement(text) {
            this.setState({ newElementText: text, resolvedVertexId: null })
        },
        onConceptSelected(concept) {
            const { conceptType } = this.props;
            this.setState({ conceptId: concept ? concept.id : conceptType })
        },
        onJustificationChanged(justification) {
            this.setState({ justification })
        },
        onVisibilityChanged(visibility) {
            this.setState({ visibility })
        },
        isValid() {
            const { resolvedVertexId, newElementText, conceptId, justification, visibility } = this.state;
            const entity = resolvedVertexId || (newElementText && conceptId);
            const others = _.all([justification, visibility], o => o && o.valid);
            return entity && others;
        }
    });

    return Resolve;
});
