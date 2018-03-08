define([
    'create-react-class',
    'prop-types',
    'classnames',
    'components/Alert',
    'components/ontology/RelationshipSelector',
    'components/justification/JustificationEditor',
    'components/visibility/VisibilityEditor',
    'util/vertex/formatters',
    'components/element/Element'
], function(
    createReactClass,
    PropTypes,
    classNames,
    Alert,
    RelationshipSelector,
    JustificationEditor,
    VisibilityEditor,
    F,
    Element) {

    const Relationship = createReactClass({
        propTypes: {
            onCancel: PropTypes.func.isRequired,
            onSave: PropTypes.func.isRequired,
            sourceVertexId: PropTypes.string.isRequired,
            targetVertexId: PropTypes.string.isRequired
        },
        getInitialState() {
            return {
                invert: false,
                visibility: { value: '', valid: true }
            };
        },
        render() {
            const { loading, error, onCancel, onSave, vertices, onFocusElements } = this.props;
            const { invert, relationshipId, justification = {}, visibility } = this.state;
            const { valid: justificationValid, value: justificationValues } = justification

            let { sourceVertexId, targetVertexId } = this.props;
            if (invert) {
                [sourceVertexId, targetVertexId] = [targetVertexId, sourceVertexId];
            }

            const sourceVertex = vertices[sourceVertexId];
            const targetVertex = vertices[targetVertexId];
            const sourceId = sourceVertex && F.vertex.prop(sourceVertex, 'conceptType')
            const targetId = targetVertex && F.vertex.prop(targetVertex, 'conceptType')

            return (
                <div className="form relationshipform">
                    { error ? (<Alert error={error} />) : null }

                    <h1>{i18n('detail.text.terms.form.relationship')}</h1>

                    <div className="rel-elements">
                        <p><Element element={sourceVertex} onFocusElements={onFocusElements} /></p>

                        <div className="rel-arrow-wrap">
                            <div className="rel-arrow"></div>
                            <button
                                title={i18n('detail.text.terms.form.relationship.invert.tooltip')}
                                onClick={this.onInvert}
                                className="invert btn btn-link btm-mini"
                            >{ i18n('detail.text.terms.form.relationship.invert')}</button>
                        </div>

                        <p><Element element={targetVertex} onFocusElements={onFocusElements} /></p>
                    </div>

                    <RelationshipSelector
                        onSelected={this.onSelected}
                        disabled={!sourceId || !targetId}
                        value={relationshipId}
                        filter={{ sourceId, targetId }} />

                    <JustificationEditor
                        value={justificationValues}
                        onJustificationChanged={this.onJustificationChanged} />

                    <VisibilityEditor
                        value={visibility && visibility.value}
                        onVisibilityChanged={this.onVisibilityChanged} />

                    <div className="buttons">
                        <button onClick={onCancel} className="btn btn-link btn-small">{i18n('detail.text.terms.form.cancel')}</button>
                        <button
                            disabled={loading || !this.isValid()}
                            onClick={this.onSave}
                            className={classNames('btn-primary btn btn-small', {loading})}>{i18n('detail.text.terms.form.create')}</button>
                    </div>
                </div>
            )
        },
        onInvert() {
            const { invert } = this.state;
            this.setState({ invert: !invert, relationshipId: null })
        },
        onJustificationChanged(justification) {
            this.setState({ justification })
        },
        onVisibilityChanged(visibility) {
            this.setState({ visibility })
        },
        onSelected(relationship) {
            this.setState({ relationshipId: relationship ? relationship.title : null });
        },
        onSave() {
            const { invert, relationshipId, justification, visibility } = this.state;

            let { sourceVertexId, targetVertexId } = this.props;
            if (invert) {
                [sourceVertexId, targetVertexId] = [targetVertexId, sourceVertexId];
            }

            this.props.onSave({
                outVertexId: sourceVertexId,
                inVertexId: targetVertexId,
                predicateLabel: relationshipId,
                visibilitySource: visibility.value,
                ...justification.value
            })
        },
        isValid() {
            const { relationshipId, justification, visibility } = this.state;
            const others = _.all([justification, visibility], o => o && o.valid);
            return relationshipId && others;
        }
    });

    return Relationship;
});
