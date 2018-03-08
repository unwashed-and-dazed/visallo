define([
    'create-react-class',
    'prop-types',
    'classnames',
    'react-virtualized',
    'util/vertex/formatters',
    'util/privileges',
    'util/dnd',
    'components/visibility/VisibilityViewer'
], function(
    createReactClass,
    PropTypes,
    classNames,
    ReactVirtualized,
    F,
    Privileges,
    dnd,
    VisibilityViewer) {
    'use strict';

    const ELEMENT_SIZE = 45;
    const PROPERTY_UPDATE_SIZE = 75;
    const PROPERTY_NEW_SIZE = 40;
    const AVERAGE_ROW_SIZE = Math.round((ELEMENT_SIZE + PROPERTY_UPDATE_SIZE + PROPERTY_NEW_SIZE) / 3);

    function lookupTitle(diffTitle, titles, vertexId) {
        let str = diffTitle;
        let loading = false;
        if (!str) {
            if (vertexId && vertexId in titles) {
                str = titles[vertexId] || i18n('vertex.property.title.not_available')
            } else {
                loading = true;
                str = i18n('workspaces.diff.title.loading');
            }
        }
        return { str, loading };
    }

    function debounceAfterFirst(fn) {
        const debounced = _.debounce(fn, 500);
        let first = true;
        return function() {
            if (first) {
                fn.apply(null, arguments);
                first = false;
            } else {
                debounced.apply(null, arguments);
            }
        }
    }

    function titlesRequest(ids) {
        return Promise.require('util/withDataRequest')
            .then(dr => {
                return dr.dataRequest('vertex', 'multiple', { vertexIds: ids })
                    .then(result => {
                        const newTitles = {};
                        result.forEach(v => {
                            if (v) {
                                newTitles[v.id] = F.vertex.title(v);
                            }
                        })
                        return newTitles;
                    })
            })
    }

    function formatVisibility(propertyOrProperties) {
        const property = Array.isArray(propertyOrProperties) ? propertyOrProperties[0] : propertyOrProperties;
        return property['http://visallo.org#visibilityJson'];
    }

    function formatValue(name, change, property) {
        return F.vertex.prop({
            id: property.id,
            properties: change ? Array.isArray(change) ? change : [change] : []
        }, name, property.key)
    }

    const DiffPanel = createReactClass({

        getInitialState() {
            return { titles: {} };
        },

        renderHeader: function(flatDiffs) {
            const {
                publishCount,
                undoCount,
                totalCount,
                ontologyRequiredCount,
                publishing,
                undoing,
                onApplyPublishClick,
                onApplyUndoClick
            } = this.props;
            const totalPublishCount = Privileges.missingONTOLOGY_PUBLISH ? (totalCount - ontologyRequiredCount) : totalCount;
            const publishingAll = publishCount > 0 && publishCount === totalPublishCount;
            const undoingAll = undoCount > 0 && undoCount === totalCount;

            return (
              <div className="diff-header">
                  {this.renderHeaderActions(publishingAll, undoingAll)}

                  <h1 className="header">
                    {publishing || publishCount > 0 ? (
                        <button className={
                            classNames('btn btn-small publish-all btn-success', {
                                loading: publishing
                            })}
                            onClick={onApplyPublishClick}
                            disabled={publishing || undoing}
                            data-count={F.number.pretty(publishCount)}>
                            { i18n('workspaces.diff.button.publish') }
                        </button>
                    ) : null}
                    {undoing || undoCount > 0 ? (
                        <button className={
                            classNames('btn btn-small undo-all btn-danger', {
                                loading: undoing
                            })}
                            onClick={onApplyUndoClick}
                            disabled={publishing || undoing}
                            data-count={F.number.pretty(undoCount)}>
                            { i18n('workspaces.diff.button.undo') }
                        </button>
                    ) : null}

                    {publishCount === 0 && undoCount === 0 ? (
                        <span>{ i18n('workspaces.diff.header.unpublished_changes') }</span>
                    ) : null}
                  </h1>
              </div>
            );
        },

        renderHeaderActions: function(publishingAll, undoingAll) {
            const { publishing, undoing, onSelectAllPublishClick, onSelectAllUndoClick, onDeselectAllClick } = this.props;
            const applying = publishing || undoing;

            return (
              <div className="select-actions">
                  <span>{ i18n('workspaces.diff.button.select_all') }</span>
                  <div className="btn-group actions">
                    {Privileges.canPUBLISH ? (
                        <button className={
                            classNames('btn btn-mini select-all-publish requires-PUBLISH', {
                                'btn-success': publishingAll
                            })}
                            onClick={publishingAll ? onDeselectAllClick : onSelectAllPublishClick}
                            disabled={applying}
                            data-action="publish">
                            {i18n('workspaces.diff.button.publish')}
                        </button>
                    ) : null}
                    {Privileges.canPUBLISH ? (
                        <button className={
                            classNames('btn btn-mini select-all-undo requires-EDIT', {
                                'btn-danger': undoingAll
                            })}
                            onClick={undoingAll ? onDeselectAllClick : onSelectAllUndoClick}
                            disabled={applying}
                            data-action="undo">
                            {i18n('workspaces.diff.button.undo')}
                        </button>
                    ) : null}
                  </div>
              </div>
            );
        },

        renderDiffActions: function(id, { publish, undo, requiresOntologyPublish }) {
            const { publishing, undoing, onPublishClick, onUndoClick } = this.props;
            const applying = publishing || undoing;
            const disabledBecauseOntologyChange = requiresOntologyPublish && Privileges.missingONTOLOGY_PUBLISH;

            return (
                <div className="actions">
                    <div className="btn-group">
                        {Privileges.canPUBLISH && !disabledBecauseOntologyChange ? (
                            <button className={
                                classNames('btn', 'btn-mini', 'publish', 'requires-PUBLISH', {
                                    'btn-success': publish
                                })}
                                onClick={e => {
                                    e.stopPropagation();
                                    onPublishClick(id);
                                }}
                                disabled={applying}>
                                {i18n('workspaces.diff.button.publish')}
                            </button>
                        ) : null}
                        {Privileges.canEDIT ? (
                            <button className={
                                classNames('btn', 'btn-mini', 'undo', 'requires-EDIT', {
                                    'btn-danger': undo
                                })}
                                onClick={e => {
                                    e.stopPropagation();
                                    onUndoClick(id);
                                }}
                                disabled={applying}>
                                {i18n('workspaces.diff.button.undo')}
                            </button>
                        ) : null}
                    </div>
                </div>
            );
        },

        renderRequiresOntologyPublish(diff) {
            return Privileges.canPUBLISH && diff.requiresOntologyPublish && Privileges.missingONTOLOGY_PUBLISH ?
                (<div className="action-subtype">{ i18n('workspaces.diff.requires.ontology.publish') }</div>) : null;
        },

        renderVertexDiff: function(key, style, diff) {
            const { titles } = this.state;
            const { action, active, className, conceptImage, deleted, publish, selectedConceptImage, title: diffTitle, undo, vertex, vertexId } = diff;
            const { onVertexRowClick } = this.props;
            const conceptImageStyle = {
                backgroundImage: conceptImage || vertex ? `url(${conceptImage || F.vertex.image(vertex, null, 80)})` : ''
            };
            const selectedConceptImageStyle = {
                backgroundImage: selectedConceptImage || vertex ? `url(${selectedConceptImage || F.vertex.selectedImage(vertex, null, 80)})` : ''
            };
            const title = lookupTitle(diffTitle, titles, vertexId);

            return (
                <div key={key} style={style}
                    className={
                    classNames('d-row', 'vertex-row', className, {
                        'mark-publish': publish,
                        'mark-undo': undo,
                        active: active,
                        deleted: deleted
                    })}
                    onClick={() => onVertexRowClick(active ? null : vertexId)}
                    draggable={true}
                    onDragStart={this.onDragStart}
                    data-diff-id={ vertexId }
                    data-vertex-id={ vertexId }>
                    <div className="vertex-label">
                        <div className="img" style={conceptImageStyle}></div>
                        <div className="selected-img" style={selectedConceptImageStyle}></div>
                        <h1 title={title.loading ? '' : title.str}>{title.loading ? (<span className="loading">{title.str}</span>) : title.str}</h1>
                        <div className="diff-action">
                            {action.type !== 'update' ? (
                                <span className="label action-type">{ action.display }</span>
                            ) : null}
                            {this.renderRequiresOntologyPublish(diff)}
                        </div>
                    </div>
                    {action.type !== 'update' ? this.renderDiffActions(vertexId, diff) : null }
                </div>
            );
        },

        onDragStart(event) {
            const { target } = event;
            if (target.dataset) {
                const { vertexId, edgeId } = target.dataset;
                const elements = { vertexIds: vertexId ? [vertexId] : [], edgeIds: edgeId ? [edgeId] : [] };
                const dt = event.dataTransfer;
                if (dt) {
                    dnd.setDataTransferWithElements(dt, elements);
                }
            }
        },

        renderEdgeDiff: function(key, style, diff) {
            const { action, active, className, deleted, edge, edgeId, edgeLabel,
                publish, undo,
                sourceId, targetId,
                sourceTitle: sourceTitleDiff, targetTitle: targetTitleDiff
            } = diff;
            const { onEdgeRowClick } = this.props;
            const { titles } = this.state;
            const sourceTitle = lookupTitle(sourceTitleDiff, titles, sourceId);
            const targetTitle = lookupTitle(targetTitleDiff, titles, targetId);

            return (
                <div key={key} style={style}
                    className={classNames('d-row', 'edge-row', className, {
                        'mark-publish': publish,
                        'mark-undo': undo,
                        active: active,
                        deleted: deleted
                    })}
                    onClick={() => onEdgeRowClick(active ? null : edgeId)}
                    draggable={true}
                    onDragStart={this.onDragStart}
                    data-diff-id={edgeId}
                    data-edge-id={edgeId}>
                    <div className="vertex-label">
                        <h1 title={`"${sourceTitle.str}" \n${edgeLabel} \n"${targetTitle.str}"`}
                            data-edge-id={ edgeId } className="edge-cont">
                            <span className={classNames({'loading': sourceTitle.loading, 'edge-v': !sourceTitle.loading})}>{sourceTitle.str}</span>
                            <span className="edge-label">{edgeLabel + ' '}</span>
                            <span className={classNames({'loading': targetTitle.loading, 'edge-v': !targetTitle.loading})}>{targetTitle.str}</span>
                        </h1>
                        <div className="diff-action">
                            {action.type !== 'update' ? (
                                <span className="label action-type">{ action.display }</span>
                            ) : null}
                            {this.renderRequiresOntologyPublish(diff)}
                        </div>
                    </div>
                    {action.type !== 'update' ?
                        this.renderDiffActions(edgeId, diff) : (
                        <div>&nbsp;</div>
                    )}
                </div>
            );
        },

        renderPropertyDiff: function(key, style, property) {
            const { className, deleted, id, name, new: nextProp, old: previousProp, publish, undo } = property;
            const { formatLabel } = this.props;
            const nextVisibility = nextProp ? formatVisibility(nextProp) : null;
            const visibility = previousProp ? formatVisibility(previousProp) : null;
            const nextValue = nextProp ? formatValue(name, nextProp, property) : null;
            const value = previousProp ? formatValue(name, previousProp, property) : null;
            const valueStyle = value !== nextValue ? { textDecoration: 'line-through'} : {};
            const visibilityStyle = visibility !== nextVisibility ? { textDecoration: 'line-through'} : {};
            const propertyNameDisplay = formatLabel(name);

            return (
                <div key={key} style={style}
                    className={classNames('d-row', className, {
                        'mark-publish': publish,
                        'mark-undo': undo
                    })}
                    data-diff-id={id}>
                <div title={propertyNameDisplay} className="property-label">{ propertyNameDisplay }</div>
                <div title={nextValue} className={classNames('property-value', { deleted: deleted })}>
                    {previousProp && nextProp ? (
                        [
                            nextValue,
                            <VisibilityViewer key={key + 'p-vis'} value={nextVisibility && nextVisibility.source} />,
                            <div title={value} key={key + 'pval'} style={valueStyle}>{value}</div>,
                            <VisibilityViewer key={key + 'p-val-vis'} style={visibilityStyle} value={visibility && visibility.source} />
                        ]
                    ) : null}
                    {!previousProp && nextProp ? (
                        [
                            nextValue,
                            <VisibilityViewer key={key + 'v'} value={nextVisibility && nextVisibility.source} />
                        ]
                    ) : null}
                </div>
                    {this.renderRequiresOntologyPublish(property)}
                    {this.renderDiffActions(id, property)}
              </div>
            );
        },

        componentDidMount() {
            this.getTitles = debounceAfterFirst(this.getTitles);
            this.scrollTop = 0;
        },

        componentDidUpdate(prevProps) {
            const { flatDiffs } = this.props;
            const { flatDiffs: previousFlatDiffs } = prevProps;
            const List = this._List;

            if (List) {
                if (previousFlatDiffs !== flatDiffs) {
                    List.recomputeRowHeights();
                }
                if (this.scrollTop > 0) {
                    // HACK: Need to pass decimal to force update. Have to look at virtualized-grid
                    List.scrollToPosition(this.scrollTop + 0.1);
                }
            }
        },

        render() {
            const { AutoSizer, List } = ReactVirtualized;
            const { flatDiffs } = this.props;
            const rowHeight = ({ index }) => (flatDiffs[index].vertex || flatDiffs[index].edge) ?
                ELEMENT_SIZE :
                flatDiffs[index].old ?
                PROPERTY_UPDATE_SIZE :
                PROPERTY_NEW_SIZE;
            const rowRenderer = this._rowRenderer;
            const onRowsRendered = ({ overscanStartIndex, overscanStopIndex, startIndex, stopIndex }) => {
                this.getTitles(startIndex, stopIndex)
            }

            return (
                <div className="diffs-list">
                    {this.renderHeader(flatDiffs)}
                    <div className="diff-cont">
                        <div className="diff-alerts" />
                        <div className="diff-content">
                            <AutoSizer>
                            {({ height, width }) => (
                                <List
                                    ref={r => {this._List = r }}
                                    width={width}
                                    height={height}
                                    rowCount={flatDiffs.length}
                                    estimatedRowSize={AVERAGE_ROW_SIZE}
                                    rowHeight={rowHeight}
                                    rowRenderer={rowRenderer}
                                    onRowsRendered={onRowsRendered}
                                    onScroll={this.onScroll}
                                />
                            )}
                            </AutoSizer>
                        </div>
                    </div>
                </div>
            );
        },

        onScroll({ scrollTop }) {
            this.scrollTop = scrollTop;
        },

        getTitles(startIndex, stopIndex) {
            if (this.titlesRequest) {
                this.titlesRequest.cancel();
            }
            const { titles } = this.state;
            const { flatDiffs } = this.props;
            const ids = Object.keys(flatDiffs.slice(startIndex, stopIndex + 1).reduce((ids, diff) => {
                if (diff.vertexId) {
                    if (!diff.title && !(diff.vertexId in titles)) ids[diff.vertexId] = true;
                } else if (diff.sourceId && diff.targetId) {
                    if (!diff.sourceTitle && !(diff.sourceId in titles)) ids[diff.sourceId] = true;
                    if (!diff.targetTitle && !(diff.targetId in titles)) ids[diff.targetId] = true;
                }
                return ids;
            }, {}))
            if (ids.length) {
                this.titlesRequest = titlesRequest(ids)
                    .then(newTitles => {
                        if (!_.isEmpty(newTitles)) {
                            const updatedTitles = { ...titles, ...newTitles };
                            this.setState({ titles: updatedTitles })
                        }
                    })
                    .catch(error => {
                        console.warn(error);
                    })
                    .finally(() => {
                        this.titlesRequest = null;
                    })
            }
        },

        _rowRenderer({ index, isScrolling, isVisible, key, parent, style }) {
            const { flatDiffs } = this.props;
            const diff = flatDiffs[index];
            const content =
                diff.vertex ? this.renderVertexDiff(key, style, diff) :
                diff.edge ? this.renderEdgeDiff(key, style, diff) :
                this.renderPropertyDiff(key, style, diff)

            return content;
        },

        propTypes: {
            flatDiffs: PropTypes.array.isRequired,
            formatLabel: PropTypes.func.isRequired,
            onPublishClick: PropTypes.func.isRequired,
            onUndoClick: PropTypes.func.isRequired,
            onSelectAllPublishClick: PropTypes.func.isRequired,
            onSelectAllUndoClick: PropTypes.func.isRequired,
            onDeselectAllClick: PropTypes.func.isRequired,
            publishing: PropTypes.bool,
            undoing: PropTypes.bool,
            onApplyPublishClick: PropTypes.func.isRequired,
            onApplyUndoClick: PropTypes.func.isRequired,
            onVertexRowClick: PropTypes.func.isRequired,
            onEdgeRowClick: PropTypes.func.isRequired
        }
    });

    return DiffPanel;
});
