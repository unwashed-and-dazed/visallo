define([
    'create-react-class',
    'prop-types',
    'react-redux',
    'util/vertex/formatters',
    'data/web-worker/store/element/selectors',
    'data/web-worker/store/element/actions',
    'data/web-worker/store/selection/actions'
], function(createReactClass, PropTypes, redux, F, elementSelectors, elementActions, selectionActions) {

    const JustificationViewer = createReactClass({
        propTypes: {
            value: PropTypes.shape({
                justificationText: PropTypes.string,
                sourceInfo: PropTypes.object
            })
        },
        componentDidMount() {
            this._checkForTitle(this.props);
        },
        componentWillReceiveProps(nextProps) {
            this._checkForTitle(nextProps);
        },
        render() {
            const { value } = this.props;
            const { justificationText, sourceInfo } = value;

            if (justificationText) {
                return this.renderJustificationText(justificationText);
            }
            if (sourceInfo) {
                return this.renderSourceInfo(sourceInfo);
            }

            return null;
        },
        renderJustificationText(justificationText) {
            return (
                <div className="viewing">
                    <div className="text"><div className="text-inner">{justificationText}</div></div>
                </div>
            );
        },
        renderSourceInfo(sourceInfo) {
            const { sourceInfoVertex, linkToSource } = this.props;
            const { snippet } = sourceInfo;
            const title = sourceInfoVertex ? F.vertex.title(sourceInfoVertex) : sourceInfoVertex === null ? i18n('element.entity.not_found') : i18n('visallo.loading');

            return (
                <div className="viewing">
                    { snippet && (<div className="text"><div className="text-inner" dangerouslySetInnerHTML={{ __html: snippet }} /></div>) }
                    <div className="source" title={title}>
                        <strong>{i18n('justification.field.reference.label')}: </strong>{
                            linkToSource !== false ?
                            (<button className="ref-title btn btn-link" onClick={this.onClick}>{title}</button>) :
                            (<span className="ref-title">{title}</span>)
                        }</div>
                </div>
            );
        },
        onClick(event) {
            event.preventDefault();
            event.stopPropagation();
            const { openSourceInfo, value } = this.props;
            openSourceInfo(value.sourceInfo);
        },
        _checkForTitle(props) {
            const { value, sourceInfoVertex, loadVertex } = props;
            if (!value) return;

            const { sourceInfo } = value;
            if (!sourceInfo) return;
            if (sourceInfoVertex) return;

            const { vertexId } = sourceInfo;
            if (!this._toRequest) this._toRequest = {};
            if (vertexId in this._toRequest) return;

            this._toRequest[vertexId] = true;
            loadVertex(vertexId);
        }
    });

    return redux.connect(
        (state, props) => {
            let sourceInfoVertex;
            if (props.value) {
                const { sourceInfo } = props.value;
                if (sourceInfo) {
                    const vertices = elementSelectors.getVertices(state);
                    sourceInfoVertex = vertices[sourceInfo.vertexId];
                }
            }
            return {
                sourceInfoVertex,
                ...props
            };
        },

        (dispatch, props) => ({
            openSourceInfo(sourceInfo) {
                const vertexId = sourceInfo.vertexId;
                const textPropertyKey = sourceInfo.textPropertyKey;
                const textPropertyName = sourceInfo.textPropertyName;
                const offsets = [sourceInfo.startOffset, sourceInfo.endOffset];
                dispatch(selectionActions.set({
                    vertices: [vertexId],
                    options: {
                        focus: {
                            vertexId,
                            textPropertyKey,
                            textPropertyName,
                            offsets
                        }
                    }
                }));
            },
            loadVertex(id) {
                dispatch(elementActions.get({ vertexIds: [id] }));
            }
        })
    )(JustificationViewer);
});
