define([
    'create-react-class',
    'prop-types',
    './Cytoscape',
    './popoverHelper',
    './styles',
    './GraphEmpty',
    './GraphExtensionViews',
    './popovers/index',
    './collapsedNodeImageHelpers',
    'util/vertex/formatters',
    'util/retina',
    'util/deepObjectCache',
    'components/RegistryInjectorHOC'
], function(
    createReactClass,
    PropTypes,
    Cytoscape,
    PopoverHelper,
    styles,
    GraphEmpty,
    GraphExtensionViews,
    Popovers,
    CollapsedNodeImageHelpers,
    F,
    retina,
    DeepObjectCache,
    RegistryInjectorHOC) {
    'use strict';

    const MaxPathsToFocus = 100;
    const MaxPreviewPopovers = 5;
    const MaxEdgesBetween = 5;
    const MaxTitleWords = 12; // Matches detail/item/layoutComponents/vertex#MaxTitleWords
    const REQUEST_UPDATE_DEBOUNCE = 300;
    const FORCE_UPDATE_DEBOUNCE = 1000;

    const noop = function() {};
    const generateCompoundEdgeId = edge => edge.outVertexId + edge.inVertexId + edge.label;
    const isGhost = cyElement => cyElement && cyElement._private && cyElement._private.data && cyElement._private.data.animateTo;
    const isValidElement = cyElement => cyElement && cyElement.is('.c,.v,.e,.partial') && !isGhost(cyElement);
    const isValidNode = cyElement => cyElement && cyElement.is('node.c,node.v,node.partial') && !isGhost(cyElement);
    const canUpdatePosition = isValidNode;
    const canSelect = isValidElement;
    const canRemove = isValidElement;
    const canPreview = node => node.hasClass('v');
    const edgeDisplay = (label, ontologyRelationships, edges) => {
        const display = label in ontologyRelationships ? ontologyRelationships[label].displayName : '';
        const showNum = edges.length > 1;
        const num = showNum ? ` (${F.number.pretty(edges.length)})` : '';
        return display + num;
    };
    const propTypesElementObjects = { vertices: PropTypes.object, edges: PropTypes.object };
    const decorationParentFor = _.memoize((id, { isFocusing, focused }) => ({
        group: 'nodes',
        data: { id },
        classes: 'decorationParent' + (
            isFocusing && focused ? ' dec-focus' :
            isFocusing && !focused ? ' dec-focus-dim' : ''
        ),
        selectable: false,
        grabbable: false
    }), (id, { isFocusing, focused}) => [id, isFocusing, focused].join(','));

    let memoizeForStorage = {};
    const memoizeClear = (...prefixes) => {
        if (prefixes.length) {
            memoizeForStorage = _.omit(memoizeForStorage, (v, k) =>
                _.any(prefixes, prefix => k.indexOf(prefix) === 0));
        } else {
            memoizeForStorage = {};
        }
    }
    const memoizeGet = function(key, elements, idFn) {
        const fullKey = `${key}-${idFn ? idFn() : elements.id}`;
        return memoizeForStorage[fullKey];
    }
    const memoizeFor = function(key, elements, fn, idFn) {
        if (!key) throw new Error('Cache key must be specified');
        if (!elements) throw new Error('Valid elements should be provided');
        if (!_.isFunction(fn)) throw new Error('Cache creation method should be provided');
        const fullKey = `${key}-${idFn ? idFn() : elements.id}`;
        const cache = memoizeForStorage[fullKey];
        const vertexChanged = cache && (_.isArray(cache.elements) ?
            (
                cache.elements.length !== elements.length ||
                _.any(cache.elements, (ce, i) => ce !== elements[i])
            ) : cache.elements !== elements
        );
        if (cache && !vertexChanged) {
            return cache.value
        }

        memoizeForStorage[fullKey] = { elements, value: fn() };
        return memoizeForStorage[fullKey].value
    }
    const styleCache = new DeepObjectCache();

    const Graph = createReactClass({

        propTypes: {
            workspace: PropTypes.shape({
                editable: PropTypes.bool
            }).isRequired,
            hasPreview: PropTypes.bool.isRequired,
            product: PropTypes.shape({
                extendedData: PropTypes.shape(propTypesElementObjects).isRequired
            }).isRequired,
            edgeLabels: PropTypes.bool,
            productElementIds: PropTypes.shape(propTypesElementObjects).isRequired,
            elements: PropTypes.shape({
                vertices: PropTypes.object,
                edges: PropTypes.object
            }).isRequired,
            selection: PropTypes.shape(propTypesElementObjects).isRequired,
            focusing: PropTypes.shape(propTypesElementObjects).isRequired,
            registry: PropTypes.object.isRequired,
            onUpdatePreview: PropTypes.func.isRequired,
            onVertexMenu: PropTypes.func,
            onEdgeMenu: PropTypes.func
        },

        getDefaultProps() {
            return {
                onVertexMenu: noop,
                onEdgeMenu: noop
            }
        },

        getInitialState() {
            return {
                animatingGhosts: {},
                draw: null,
                paths: null,
                hovering: null,
                collapsedImageDataUris: {}
            }
        },

        componentWillMount() {
            this.requestUpdateDebounce = _.debounce(this.requestUpdate, REQUEST_UPDATE_DEBOUNCE)
            this.forceUpdateDebounce = _.debounce(() => {
                if (this.mounted) {
                    this.forceUpdate()
                }
            }, FORCE_UPDATE_DEBOUNCE);
        },

        componentDidMount() {
            this.mounted = true;
            memoizeClear();
            this.cyNodeIdsWithPositionChanges = {};

            this.popoverHelper = new PopoverHelper(this.node, this.cy);
            this.legacyListeners({
                addRelatedDoAdd: (event, data) => {
                    this.props.onAddRelated(this.props.product.id, data.addVertices)
                },
                selectAll: (event, data) => {
                    this.cytoscape.state.cy.elements().select();
                },
                selectConnected: (event, data) => {
                    event.stopPropagation();
                    const cy = this.cytoscape.state.cy;
                    let selected = cy.elements().filter(':selected');

                    if (selected.length === 0) {
                        const id = data.collapsedNodeId || data.vertexId || data.edgeIds[0];
                        selected = cy.getElementById(id);

                        if (selected.length === 0) {
                            cy.edges().filter(edge => edge.data('edgeInfos').some(edgeInfo => edgeInfo.edgeId === id))
                        }
                    }

                    selected.neighborhood('node').select();
                    selected.connectedNodes().select();

                    selected.unselect();
                },
                startVertexConnection: (event, { vertexId, connectionType }) => {
                    $(document).trigger('defocusPaths');
                    this.setState({
                        draw: {
                            vertexId,
                            connectionType
                        }
                    });
                },
                editCollapsedNode: (event, { collapsedNodeId }) => { this.onEditCollapsedNode(collapsedNodeId)},
                selectCollapsedNodeContents: (event, { collapsedNodeId, select }) => {
                    this.onSelectCollapsedNodeContents(collapsedNodeId, select)
                },
                uncollapse: (event, { collapsedNodeId }) => {
                    this.props.onUncollapseNodes(this.props.product.id, collapsedNodeId);
                },
                menubarToggleDisplay: { node: document, handler: (event, data) => {
                    if (data.name === 'products-full') {
                        this.teardownPreviews();
                    }
                }},
                finishedVertexConnection: this.cancelDraw,
                'zoomOut zoomIn fit': this.onKeyboard,
                createVertex: event => this.createVertex(),
                fileImportSuccess: { node: $('.products-full-pane.visible')[0], handler: this.onFileImportSuccess },
                previewVertex: this.previewVertex,
                closePreviewVertex: (event, { vertexId }) => {
                    delete this.detailPopoversMap[vertexId];
                },
                elementsCut: { node: document, handler: (event, { vertexIds }) => {
                    this.props.onRemoveElementIds({ vertexIds, edgeIds: [] });
                }},
                elementsPasted: { node: document, handler: (event, elementIds) => {
                    this.props.onDropElementIds(elementIds)
                }},
                focusPaths: { node: document, handler: this.onFocusPaths },
                defocusPaths: { node: document, handler: this.onDefocusPaths },
                focusPathsAddVertexIds: { node: document, handler: this.onFocusPathsAdd },
                reapplyGraphStylesheet: { node: document, handler: this.reapplyGraphStylesheet }
            });
        },

        componentWillReceiveProps(nextProps) {
            if (nextProps.selection !== this.props.selection) {
                this.resetQueuedSelection(nextProps.selection);
            }
            if (nextProps.registry !== this.props.registry) {
                memoizeClear();
            }

            const clearForOntologyUpdates = [];
            if (nextProps.concepts !== this.props.concepts || nextProps.properties !== this.props.properties) {
                clearForOntologyUpdates.push(
                    'vertexToCyNode',
                    'org.visallo.graph.node.class',
                    'org.visallo.graph.node.transformer',
                    'org.visallo.graph.node.decoration#applyTo',
                    'org.visallo.graph.node.decoration#data'
                )
            }
            if (nextProps.relationships !== this.props.relationships) {
                clearForOntologyUpdates.push(
                    'vertexToCyNode',
                    'org.visallo.graph.edge.transformer',
                    'org.visallo.graph.edge.class')
            }
            if (clearForOntologyUpdates.length) {
                memoizeClear(...clearForOntologyUpdates);
            }

            const newExtendedData = nextProps.product.extendedData;
            const oldExtendedData = this.props.product.extendedData;
            if (newExtendedData) {
                let shouldClear = false;
                let shouldDefocusPaths = false;
                const ignoredExtendedDataKeys = ['vertices', 'edges', 'unauthorizedEdgeIds', 'compoundNodes'];

                Object.keys(newExtendedData).forEach(key => {
                    if (!oldExtendedData || newExtendedData[key] !== oldExtendedData[key]) {
                        if (ignoredExtendedDataKeys.includes(key)) {
                            shouldDefocusPaths = true;
                        } else {
                            shouldClear = true;
                        }
                    }
                })

                if (shouldClear) {
                    memoizeClear(
                        'vertexToCyNode',
                        'org.visallo.graph.edge.class',
                        'org.visallo.graph.edge.transformer',
                        'org.visallo.graph.node.class'
                    );
                }

                if (shouldDefocusPaths) {
                    $(document).trigger('defocusPaths');
                }
            }
            if (nextProps.product.id !== this.props.product.id) {
                this.teardownPreviews();
            }
        },

        componentWillUnmount() {
            this.mounted = false;
            styleCache.clear();
            this.removeEvents.forEach(({ node, func, events }) => {
                $(node).off(events, func);
            })

            this.teardownPreviews();
            this.popoverHelper.destroy();
            this.popoverHelper = null;
        },

        teardownPreviews(vertexIds) {
            if (this.detailPopoversMap) {
                const updatePreviews = vertexIds || Object.keys(this.detailPopoversMap);
                _.mapObject(this.detailPopoversMap, (e, id) => {
                    if (updatePreviews.includes(id)) {
                        $(e).teardownAllComponents()
                    }
                    delete this.detailPopoversMap[id]
                });
                this.detailPopoversMap = {};
            }
        },

        render() {
            var { draw, paths } = this.state,
                { panelPadding, registry, workspace, product, interacting, hasPreview } = this.props,
                { editable } = workspace,
                config = CONFIGURATION(this.props),
                events = {
                    onSelect: this.onSelect,
                    onRemove: this.onRemove,
                    onUnselect: this.onUnselect,
                    onFree: this.onFree,
                    onLayoutStop: this.onLayoutStop,
                    onPosition: this.onPosition,
                    onReady: this.onReady,
                    onDecorationEvent: this.onDecorationEvent,
                    onMouseOver: this.onMouseOver,
                    onMouseOut: this.onMouseOut,
                    onTap: this.onTap,
                    onTapHold: this.onTapHold,
                    onTapStart: this.onTapStart,
                    onTapEnd: this.onTapEnd,
                    onCxtTapStart: this.onTapStart,
                    onCxtTapEnd: this.onCxtTapEnd,
                    onContextTap: this.onContextTap
                },
                menuHandlers = {
                    onMenuCreateVertex: this.onMenuCreateVertex,
                    onMenuSelect: this.onMenuSelect,
                    onMenuExport: this.onMenuExport,
                    onCollapseSelectedNodes: this.onCollapseSelectedNodes
                },
                cyElements = this.mapPropsToElements(editable),
                extensionViews = registry['org.visallo.graph.view'];

            return (
                <div ref={r => {this.node = r}} className="org-visallo-graph" style={{ height: '100%' }}>
                    <Cytoscape
                        ref={r => { this.cytoscape = r}}
                        {...events}
                        {...menuHandlers}
                        product={product}
                        requestUpdate={this.requestUpdateDebounce}
                        hasPreview={hasPreview}
                        config={config}
                        panelPadding={panelPadding}
                        elements={cyElements}
                        interacting={interacting}
                        drawEdgeToMouseFrom={draw ? _.pick(draw, 'vertexId', 'toVertexId') : null }
                        drawPaths={paths}
                        onGhostFinished={this.props.onGhostFinished}
                        onUpdatePreview={this.onUpdatePreview}
                        editable={editable}
                        reapplyGraphStylesheet={this.reapplyGraphStylesheet}
                    ></Cytoscape>

                    {cyElements.nodes.length === 0 ? (
                        <GraphEmpty editable={editable} panelPadding={panelPadding} onSearch={this.props.onSearch} onCreate={this.onCreate} />
                    ) : null}

                    { extensionViews.length ? (
                        <GraphExtensionViews views={extensionViews} panelPadding={panelPadding} />
                    ) : null }
                </div>
            )
        },

        onFocusPaths(event, data) {
            const cy = this.cytoscape.state.cy;
            const collapsedNodes = cy.nodes().filter('node.c');
            const mappedIds = {};
            const findRenderedNodeId = (id) => {
                if (!(id in mappedIds)) {
                    if (cy.getElementById(id).length) {
                        mappedIds[id] = id
                    } else {
                        const parentNode = collapsedNodes.filter(node => node.data('vertexIds').includes(id))
                        mappedIds[id] = parentNode.length === 1 ? parentNode.id() : null
                    }
                }

                return mappedIds[id]
            }

            if (data.paths.length > MaxPathsToFocus) {
                data.paths = data.paths.slice(0, MaxPathsToFocus);
                $(document).trigger('displayInformation', {
                    message: i18n('org.visallo.web.product.graph.findPath.too.many', MaxPathsToFocus)
                })
            }

            data.renderedPaths = data.paths.reduce((renderedPaths, path) => {
                const sourceId = findRenderedNodeId(path[0])
                const targetId = findRenderedNodeId(path[(path.length - 1)])

                if (sourceId && targetId) {
                    const renderedPath = path.map(findRenderedNodeId)
                    renderedPaths.push(renderedPath)
                }

                return renderedPaths
            }, [])

            this.setState({
                paths: data
            })
        },

        onFocusPathsAdd(event) {
            const { paths } = this.state;
            if (paths) {
                const limitedPaths = paths.paths.slice(0, MaxPathsToFocus);
                const vertexIds = _.chain(limitedPaths).flatten().uniq().value();
                this.props.onDropElementIds({ vertexIds });
            }
        },

        onDefocusPaths(event, data) {
            if (this.state.paths) {
                this.setState({ paths: null });
            }
        },

        onCreate() {
            this.createVertex();
        },

        reapplyGraphStylesheet() {
            this.requestUpdateDebounce();
        },

        requestUpdate() {
            if (this.mounted) {
                memoizeClear();
                styleCache.clear();
                this.forceUpdate();
            }
        },

        onReady({ cy }) {
            this.cy = cy;
        },

        onDecorationEvent(event) {
            const { cy, target } = event;
            const decoration = decorationForId(target.id());
            if (decoration) {
                const handlerName = {
                    /**
                     * @callback org.visallo.graph.node.decoration~onClick
                     * @this The decoration cytoscape node
                     * @param {object} event The {@link http://js.cytoscape.org/#events/event-object|Cytoscape event} object
                     * @param {object} data
                     * @param {object} data.vertex The vertex this decoration
                     * is attached
                     * @param {object} data.cy The cytoscape instance
                     */
                    tap: 'onClick',
                    /**
                     * @callback org.visallo.graph.node.decoration~onMouseOver
                     * @this The decoration cytoscape node
                     * @param {object} event The {@link http://js.cytoscape.org/#events/event-object|Cytoscape event} object
                     * @param {object} data
                     * @param {object} data.vertex The vertex this decoration
                     * is attached
                     * @param {object} data.cy The cytoscape instance
                     */
                    mouseover: 'onMouseOver',
                    /**
                     * @callback org.visallo.graph.node.decoration~onMouseOut
                     * @this The decoration cytoscape node
                     * @param {object} event The {@link http://js.cytoscape.org/#events/event-object|Cytoscape event} object
                     * @param {object} data
                     * @param {object} data.vertex The vertex this decoration
                     * is attached
                     * @param {object} data.cy The cytoscape instance
                     */
                    mouseout: 'onMouseOut'
                }[event.type];
                if (_.isFunction(decoration.onClick)) {
                    if (handlerName === 'onMouseOver') {
                        this.node.style.cursor = 'pointer';
                    } else if (handlerName === 'onMouseOut' || handlerName === 'onClick') {
                        this.node.style.cursor = null;
                    }
                }
                if (_.isFunction(decoration[handlerName])) {
                    decoration[handlerName].call(target, event, {
                        cy,
                        vertex: target.data('vertex')
                    });
                }
            }
        },

        onMouseOver({ cy, target }) {
            clearTimeout(this.hoverMouseOverTimeout);

            if (target !== cy && target.is('node.v,node.c')) {
                this.hoverMouseOverTimeout = _.delay(() => {
                    const hovering = target.id();
                    if (hovering !== this.state.hovering) {
                        this.setState({ hovering })
                    }
                }, 500);
            }
        },

        onMouseOut({ cy, target }) {
            clearTimeout(this.hoverMouseOverTimeout);
            if (target !== cy && target.is('node.v,node.c')) {
                if (this.state.hovering) {
                    this.setState({ hovering: null })
                }
            }
        },

        onFileImportSuccess(event, { vertexIds, position }) {
            const { x, y } = position;
            const { left, top } = this.node.getBoundingClientRect();
            const pos = this.droppableTransformPosition({
                x: x - left,
                y: y - top
            });
            this.props.onDropElementIds({vertexIds}, pos);
        },

        onKeyboard(event) {
            const { type } = event;
            const cytoscape = this.cytoscape;

            switch (type) {
                case 'fit': cytoscape.fit();
                    break;
                case 'zoomIn': cytoscape.onControlsZoom('in')
                    break;
                case 'zoomOut': cytoscape.onControlsZoom('out')
                    break;
                default:
                    console.warn(type);
            }
        },

        onMenuSelect(select) {
            const { registry, productElementIds, selection: productSelection, onClearSelection, onSetSelection } = this.props;
            const selection = {};

            this.coalesceSelection('clear');

            switch (select) {
                case 'all':
                    selection.vertices = Object.keys(productElementIds.vertices);
                    selection.edges = Object.keys(productElementIds.edges);
                    break;
                case 'invert':
                    const selectedVertexIds = Object.keys(productSelection.vertices);
                    const selectedEdgeIds = Object.keys(productSelection.edges);
                    const unselectedVertexIds = _.difference(Object.keys(productElementIds.vertices), selectedVertexIds);
                    const unselectedEdgeIds = _.difference(Object.keys(productElementIds.edges), selectedEdgeIds);

                    selection.vertices = unselectedVertexIds;
                    selection.edges = unselectedEdgeIds;
                    break;
                case 'vertices':
                    selection.vertices = Object.keys(productElementIds.vertices);
                    selection.edges = [];
                    break;
                case 'edges':
                    selection.vertices = [];
                    selection.edges = Object.keys(productElementIds.edges);
                    break;
                case 'none':
                    onClearSelection();
                    return;
                default:
                    const selector = registry['org.visallo.graph.selection'].find(e => e.identifier === select);
                    if (selector) {
                        selector(this.cytoscape.state.cy);
                    }
                    return;
            }

            onSetSelection(selection);
        },

        onMenuExport(componentPath) {
            var exporter = _.findWhere(
                    this.props.registry['org.visallo.graph.export'],
                    { componentPath }
                );

            if (exporter) {
                const cy = this.cytoscape.state.cy;
                const { product } = this.props;
                Promise.require('util/popovers/exportWorkspace/exportWorkspace').then(ExportWorkspace => {
                    ExportWorkspace.attachTo(cy.container(), {
                        exporter: exporter,
                        workspaceId: product.workspaceId,
                        productId: product.id,
                        cy: cy,
                        anchorTo: {
                            page: {
                                x: window.lastMousePositionX,
                                y: window.lastMousePositionY
                            }
                        }
                    });
                });
            }
        },

        onCollapseSelectedNodes(nodes) {
            const { product, rootId } = this.props;

            if (nodes.length < 2) return;

            const children = nodes.map(node => node.id());
            const positions = nodes.map(node => retina.pixelsToPoints(node.position()));
            const pos = {
                x: Math.round(positions.reduce((total, pos) => total + pos.x, 0) / positions.length),
                y: Math.round(positions.reduce((total, pos) => total + pos.y, 0) / positions.length)
            };

            this.props.onCollapseNodes(product.id, {
                children,
                pos,
                parent: rootId
            });

            let vertexIds = [];
            _.each(nodes, node => {
                if (node.data('vertexIds')) {
                    vertexIds = vertexIds.concat(node.data('vertexIds'));
                } else {
                    vertexIds.push(node.id());
                }
            });
            this.teardownPreviews(vertexIds);
        },

        onMenuCreateVertex({pageX, pageY }) {
            const position = { x: pageX, y: pageY };
            this.createVertex(position);
        },

        previewVertex(event, data) {
            const cy = this.cytoscape.state.cy;

            Promise.all([
                Promise.require('util/popovers/detail/detail'),
                F.vertex.getVertexIdsFromDataEventOrCurrentSelection(data, { async: true })
            ]).spread((DetailPopover, ids) => {
                if (!this.detailPopoversMap) {
                    this.detailPopoversMap = {};
                }
                const currentPopovers = Object.keys(this.detailPopoversMap);
                const remove = _.intersection(ids, currentPopovers);
                var add = _.difference(ids, currentPopovers)

                remove.forEach(id => {
                    const cyNode = cy.getElementById(id);
                    if (cyNode.length) {
                        $(this.detailPopoversMap[id]).teardownAllComponents().remove();
                        delete this.detailPopoversMap[id];
                    }
                })
                const availableToOpen = MaxPreviewPopovers - (currentPopovers.length - remove.length);
                if (add.length && add.length > availableToOpen) {
                    $(this.node).trigger('displayInformation', { message: i18n('popovers.preview_vertex.too_many', MaxPreviewPopovers) });
                    add = add.slice(0, Math.max(0, availableToOpen));
                }

                add.forEach(id => {
                    var $popover = $('<div>').addClass('graphDetailPanePopover').appendTo(this.node);
                    this.detailPopoversMap[id] = $popover[0];
                    DetailPopover.attachTo($popover[0], {
                        vertexId: id,
                        anchorTo: {
                            vertexId: id
                        }
                    });
                })
            });
        },

        createVertex(position) {
            if (!position) {
                position = { x: window.lastMousePositionX, y: window.lastMousePositionY };
            }

            if (this.props.workspace.editable) {
                Promise.require('util/popovers/fileImport/fileImport')
                    .then(CreateVertex => {
                        CreateVertex.attachTo(this.node, {
                            anchorTo: { page: position }
                        });
                    });
            }
        },

        onEditCollapsedNode(collapsedNodeId) {
            const collapsedNode = this.cytoscape.state.cy.getElementById(collapsedNodeId);
            if (this.props.workspace.editable && collapsedNode) {
                Promise.require('org/visallo/web/product/graph/popovers/collapsedNode/collapsedNodePopoverShim')
                    .then(CollapsedNodePopover => {
                        CollapsedNodePopover.attachTo(this.node, {
                            cy: this.cytoscape.state.cy,
                            cyNode: collapsedNode,
                            props: {
                                onRename: this.props.onRenameCollapsedNode.bind(this, this.props.product.id, collapsedNodeId),
                                collapsedNodeId: collapsedNodeId
                            },
                            teardownOnTap: true
                        });
                    });
            }
        },

        onSelectCollapsedNodeContents(collapsedNodeIds, select) {
            const { selection, product, elements, onSetSelection, onAddSelection, onRemoveSelection } = this.props;
            const { compoundNodes: collapsedNodes } = product.extendedData;
            let vertices = [];
            let edges = [];

            collapsedNodeIds = _.isArray(collapsedNodeIds) ? collapsedNodeIds : [collapsedNodeIds];

            collapsedNodeIds.forEach(id => {
                vertices = vertices.concat(getVertexIdsFromCollapsedNode(collapsedNodes, id));
                edges = edges.concat(_.values(elements.edges).reduce((ids, edge) => {
                    if (vertices.includes(edge.inVertexId) && vertices.includes(edge.outVertexId)) {
                        ids.push(edge.id);
                    }
                    return ids;
                }, []));
            });

            switch (select) {
                case 'all':
                    onAddSelection({ vertices, edges });
                    break;
                case 'none':
                    onRemoveSelection({ vertices, edges });
                    break;
                case 'vertices':
                    onSetSelection({
                        vertices: _.uniq(Object.keys(selection.vertices).concat(vertices)),
                        edges: _.without(Object.keys(selection.edges), edges)
                    });
                    break;
                case 'edges':
                    onSetSelection({
                        vertices: _.without(Object.keys(selection.vertices), vertices),
                        edges: _.uniq(Object.keys(selection.edges).concat(edges))
                    });
                    break;
            }
        },

        onUpdatePreview(data) {
            this.props.onUpdatePreview(this.props.product.id, data)
        },

        cancelDraw() {
            const cy = this.cytoscape.state.cy;
            cy.autoungrabify(false);
            this.setState({ draw: null })
        },

        onTapHold({ cy, target }) {
            if (cy !== target) {
                if (canPreview(target)) {
                    this.previewVertex(null, { vertexId: target.id() })
                }
            }
        },

        onTapStart(event) {
            const { cy, target } = event;

            if (event.originalEvent.ctrlKey || event.originalEvent.metaKey) {
                cy.boxSelectionEnabled(false);
            }

            if (cy !== target && event.originalEvent.ctrlKey) {
                cy.autoungrabify(true);
                if (target.hasClass('v')) {
                    $(document).trigger('defocusPaths');
                    this.setState({
                        draw: {
                            vertexId: target.id()
                        }
                    });
                }
            }
        },

        onTap(event) {
            const { cy, target, position } = event;
            const { x, y } = position;
            const { ctrlKey, shiftKey, metaKey } = event.originalEvent;
            const { draw, paths } = this.state;

            if (paths) {
                if (cy === target && _.isEmpty(this.props.selection.vertices) && _.isEmpty(this.props.selection.edges)) {
                    $(document).trigger('defocusPaths');
                    this.setState({ paths: null })
                }
            }
            if (draw) {
                const upElement = cy.renderer().findNearestElement(x, y, true, false);
                if (!upElement || draw.vertexId === upElement.id() || draw.toVertexId) {
                    this.cancelDraw();
                    if (ctrlKey && upElement) {
                        this.onContextTap(event);
                    }
                } else if (!upElement.hasClass('v')) {
                    this.cancelDraw();
                } else {
                    this.setState({ draw: {...draw, toVertexId: upElement.id() } });
                    this.showConnectionPopover();
                }
            } else {
                if (ctrlKey) {
                    this.onContextTap(event);
                } else if (!shiftKey && cy === target) {
                    this.coalesceSelection('clear');
                    this.props.onClearSelection();
                } else if (!shiftKey && !metaKey && canSelect(target)) {
                    this.coalesceSelection('clear');
                    this.coalesceSelection('add', getCyItemTypeAsString(target), target);
                }

            }
        },

        onTapEnd(event) {
            const { cy, target } = event;

            if (!cy.boxSelectionEnabled()) {
                cy.boxSelectionEnabled(true);
            }
        },

        onCxtTapEnd(event) {
            const { cy, target } = event;
            if (cy !== target && event.originalEvent.ctrlKey) {
                this.onTap(event);
            }
        },

        onContextTap(event) {
            const { target, cy, originalEvent } = event;
            // TODO: show all selected objects if not on item
            if (target !== cy) {
                const { pageX, pageY } = originalEvent;
                if (target.is('node.c')) {
                    this.props.onCollapsedItemMenu(originalEvent.target, target.id(), { x: pageX, y: pageY });
                } else if (isValidElement(target)) {
                    if (target.isNode()) {
                        this.props.onVertexMenu(originalEvent.target, target.id(), { x: pageX, y: pageY });
                    } else {
                        const edgeIds = _.pluck(target.data('edgeInfos'), 'edgeId');
                        this.props.onEdgeMenu(originalEvent.target, edgeIds, { x: pageX, y: pageY });
                    }
                }
            }
        },

        onRemove({ target }) {
            if (canRemove(target)) {
                this.coalesceSelection('remove', getCyItemTypeAsString(target), target);
            }
        },

        onSelect({ target }) {
            if (canSelect(target)) {
                this.coalesceSelection('add', getCyItemTypeAsString(target), target);
            }
        },

        onUnselect({ target }) {
            if (canSelect(target)) {
                this.coalesceSelection('remove', getCyItemTypeAsString(target), target);
            }
        },

        onLayoutStop() {
            this.sendPositionUpdates();
        },

        onFree() {
            this.sendPositionUpdates();
        },

        sendPositionUpdates() {
            const { vertices, compoundNodes: collapsedNodes } = this.props.product.extendedData;

            if (!_.isEmpty(this.cyNodeIdsWithPositionChanges)) {
                const positionUpdates = _.mapObject(this.cyNodeIdsWithPositionChanges, (cyNode, id) => {
                    const update = vertices[id] || collapsedNodes[id];
                    update.pos = retina.pixelsToPoints(cyNode.position());
                    return update;
                });

                this.props.onUpdatePositions(
                    this.props.product.id,
                    positionUpdates
                );
                this.cyNodeIdsWithPositionChanges = {};
            }
        },

        onPosition({ target }) {
            if (canUpdatePosition(target)) {
                var id = target.id();
                this.cyNodeIdsWithPositionChanges[id] = target;
            }
        },

        droppableTransformPosition(rpos) {
            const cy = this.cytoscape.state.cy;
            const pan = cy.pan();
            const zoom = cy.zoom();
            return retina.pixelsToPoints({
                x: (rpos.x - pan.x) / zoom,
                y: (rpos.y - pan.y) / zoom
            });
        },

        getRootNode() {
            const { product, productElementIds, rootId } = this.props;
            const productVertices = productElementIds.vertices;
            const collapsedNodes = product.extendedData.compoundNodes;

            if (collapsedNodes[rootId] && collapsedNodes[rootId].visible) {
                return collapsedNodes[rootId];
            } else {
                const children = [];

                [productVertices, collapsedNodes].forEach((type) => {
                    _.mapObject(type, (item, id) => {
                        if (item.parent === 'root') {
                           children.push(id);
                        }
                    })
                });

                return { id: 'root', children }
            }
        },

        mapPropsToElements(editable) {
            const { selection, ghosts, productElementIds, elements, relationships, registry, focusing, product } = this.props;
            const { hovering, collapsedImageDataUris } = this.state;
            const { vertices: productVertices, edges: productEdges } = productElementIds;
            const { vertices, edges } = elements;
            const { vertices: verticesSelectedById, edges: edgesSelectedById } = selection;
            const collapsedNodes = _.pick(product.extendedData.compoundNodes, ({ visible }) => visible);

            const rootNode = this.getRootNode();
            const filterByRoot = (items) => _.values(_.pick(items, rootNode.children));

            const cyNodeConfig = (node) => {
                // FIXME
                // change ancillary to string with type so we can more
                // easily filter extensions

                const { id, type, pos, title, ancillary } = node;
                let selectable = true, selected, classes, data;

                if (ancillary) {
                    selected = false;
                    selectable = false;
                    classes = 'ancillary';
                    registry['org.visallo.graph.ancillary'].forEach(({
                        canHandle, data: dataFn, classes: classFn
                    }) => {
                        const vertexReady = id in vertices;
                        if (canHandle(node) && vertexReady) {
                            if (dataFn) {
                                data = { ...dataFn(node, vertices[id]), id };
                            }
                            if (classFn) {
                                const classList = classFn(node, vertices[id])
                                if (classList.length) {
                                    classes += ' ' + classList.join(' ')
                                }
                            }
                        }
                    })

                    if (data) {
                        renderedNodeIds[id] = true;
                    } else {
                        data = { id }
                        classes = 'unhandled';
                    }
                } else if (type === 'vertex') {
                    selected = id in verticesSelectedById;
                    classes = mapVertexToClasses(id, vertices, focusing, hovering, registry['org.visallo.graph.node.class']);
                    data = mapVertexToData(id, vertices, registry['org.visallo.graph.node.transformer']);

                    if (data) {
                        renderedNodeIds[id] = true;
                    }
                } else {
                    const vertexIds = getVertexIdsFromCollapsedNode(collapsedNodes, id);
                    selected = vertexIds.some(id => id in verticesSelectedById)
                    classes = mapCollapsedNodeToClasses(id, collapsedNodes, focusing, vertexIds, hovering, registry['org.visallo.graph.collapsed.class']);
                    const nodeTitle = title || generateCollapsedNodeTitle(node, vertices, productVertices, collapsedNodes);
                    data = {
                        ...node,
                        vertexIds,
                        title: nodeTitle,
                        imageSrc: this.state.collapsedImageDataUris[id] && this.state.collapsedImageDataUris[id].imageDataUri || 'img/loading-large@2x.png'
                    };
                }

                return {
                    group: 'nodes',
                    data,
                    classes,
                    position: retina.pointsToPixels(pos),
                    selectable,
                    selected,
                    grabbable: editable
                }
            }

            const renderedNodeIds = {};

            const cyVertices = filterByRoot(productVertices).reduce((nodes, nodeData) => {
                const { id, parent, ancillary } = nodeData;
                const cyNode = cyNodeConfig(nodeData);

                if (ghosts && id in ghosts) {
                    const ghostData = {
                        ...cyNode.data,
                        id: `${cyNode.data.id}-ANIMATING`,
                        animateTo: {
                            id: nodeData.id,
                            pos: { ...cyNode.position }
                        }
                    };
                    delete ghostData.parent;
                    nodes.push({
                        ...cyNode,
                        data: ghostData,
                        position: retina.pointsToPixels(ghosts[id]),
                        grabbable: false,
                        selectable: false
                    });
                }

                if (parent !== rootNode.id) {
                    return nodes;
                }

                if (id in vertices) {
                    const markedAsDeleted = vertices[id] === null;
                    if (markedAsDeleted) {
                        return nodes;
                    }
                    const vertex = vertices[id];
                    const applyDecorations = !ancillary && memoizeFor('org.visallo.graph.node.decoration#applyTo', vertex, () => {
                        return _.filter(registry['org.visallo.graph.node.decoration'], function(e) {
                            /**
                             * @callback org.visallo.graph.node.decoration~applyTo
                             * @param {object} vertex
                             * @returns {boolean} Whether the decoration should be
                             * added to the node representing the vertex
                             */
                            return !_.isFunction(e.applyTo) || e.applyTo(vertex);
                        });
                    });
                    if (applyDecorations && applyDecorations.length) {
                        const parentId = 'decP' + id;
                        cyNode.data.parent = parentId;
                        const decorations = memoizeFor('org.visallo.graph.node.decoration#data', vertex, () => {
                            return applyDecorations.map((dec, index) => {
                                const data = mapDecorationToData(dec, vertex, data => {
                                    if (data) {
                                        const cache = memoizeGet('org.visallo.graph.node.decoration#data', vertex);
                                        if (cache && index < cache.value.length) {
                                            cache.value[index] = cyDecoration(data);
                                            this.forceUpdateDebounce();
                                        }
                                    }
                                })
                                if (data) {
                                    return cyDecoration(data);
                                }

                                function cyDecoration(data) {
                                    var { padding } = dec;
                                    return {
                                        group: 'nodes',
                                        classes: mapDecorationToClasses(dec, vertex),
                                        data: {
                                            ...data,
                                            id: idForDecoration(dec, vertex.id),
                                            alignment: dec.alignment,
                                            padding,
                                            parent: parentId,
                                            vertex
                                        },
                                        position: { x: -1, y: -1 },
                                        grabbable: false,
                                        selectable: false
                                    }
                                }
                            })
                        });

                        nodes.push(decorationParentFor(parentId, {
                            isFocusing: focusing.isFocusing,
                            focused: id in focusing.vertices
                        }))
                        nodes.push(cyNode);
                        decorations.forEach(d => {
                            if (d) nodes.push(d);
                        });
                    } else if (cyNode) {
                        nodes.push(cyNode);
                    }
                } else if (cyNode) {
                    nodes.push(cyNode);
                }

                return nodes
            }, []);

            _.defer(() => {
                CollapsedNodeImageHelpers.updateImageDataUrisForCollapsedNodes(
                    collapsedNodes,
                    vertices,
                    rootNode,
                    collapsedImageDataUris,
                    (newCollapsedImageDataUris) => {
                        this.setState({
                            collapsedImageDataUris: {
                                ...collapsedImageDataUris,
                                ...newCollapsedImageDataUris
                            }
                        });
                    }
                );
            });

            const cyCollapsedNodes = filterByRoot(collapsedNodes).reduce((nodes, nodeData) => {
                const cyNode = cyNodeConfig(nodeData);

                if (nodeData.children.some(childId => productVertices[childId] && !productVertices[childId].unauthorized)) {
                    renderedNodeIds[nodeData.id] = true;

                    if (ghosts) {
                        _.mapObject(ghosts, (ghost, ghostId) => {
                            if (cyNode.data.vertexIds.includes(ghostId)) {
                                const ghostData = {
                                    ...mapVertexToData(ghostId, vertices, registry['org.visallo.graph.node.transformer']),
                                    parent: rootNode.id,
                                    id: `${ghostId}-ANIMATING`,
                                    animateTo: {
                                        id: ghostId,
                                        pos: {...cyNode.position}
                                    }
                                };

                                nodes.push({
                                    ...cyNode,
                                    data: ghostData,
                                    classes: mapVertexToClasses(ghostId, vertices, focusing, hovering, registry['org.visallo.graph.node.class']),
                                    position: retina.pointsToPixels(ghosts[nodeData.id]),
                                    grabbable: false,
                                    selectable: false
                                });
                            }
                        });
                    }

                    nodes.push(cyNode);
                }

                return nodes;
            }, []);

            const cyNodes = cyVertices.concat(cyCollapsedNodes);

            const cyEdges = _.chain(productEdges)
                .filter(edgeInfo => {
                    const elementMarkedAsDeletedInStore =
                        edgeInfo.edgeId in edges &&
                        edges[edgeInfo.edgeId] === null;
                    return !elementMarkedAsDeletedInStore;
                })
                .groupBy(generateCompoundEdgeId)
                .map((edgeInfos, id) => {
                    const {inVertexId, outVertexId} = edgeInfos[0];
                    const edge = {
                        inNodeId: getRenderedNodeFromVertexId(inVertexId),
                        outNodeId: getRenderedNodeFromVertexId(outVertexId),
                        edgeInfos,
                        id
                    };
                    return edge;

                    function getRenderedNodeFromVertexId(vertexId) {
                        const vertex = productVertices[vertexId];
                        if (!vertex) return null;

                        let parentId = vertex.parent;
                        while (parentId !== rootNode.id && !(parentId in renderedNodeIds)) {
                            const parent = collapsedNodes[parentId];
                            if (!parent) return null;
                            parentId = parent.parent;
                        }
                        return parentId === rootNode.id ? vertexId : parentId;
                    }
                })
                .filter(({inNodeId, outNodeId}) => {
                    return inNodeId && outNodeId && (inNodeId !== outNodeId);
                })
                .groupBy(({ inNodeId, outNodeId }) => (inNodeId < outNodeId ? inNodeId + outNodeId : outNodeId + inNodeId))
                .reduce((edgeGroups, edgeGroup) => {
                    if (edgeGroup.length > MaxEdgesBetween) {
                        const { inNodeId, outNodeId } = edgeGroup[0];
                        const edgeInfos = edgeGroup.reduce((infos, group) => [...infos, ...group.edgeInfos], []);
                        const edgesForInfos = Object.values(_.pick(edges, _.pluck(edgeInfos, 'edgeId')));
                        const multiEdgeLabel = (edges) => {
                            const numTypes = _.size(_.groupBy(edgesForInfos, 'label'));
                            const display = edges[0] ?
                                edges[0].label in relationships ?
                                    relationships[edges[0].label].displayName : '' :
                                '';

                            return numTypes === 1 ?
                                i18n('org.visallo.web.product.graph.multi.edge.label.single.type', edges.length, display) :
                                i18n('org.visallo.web.product.graph.multi.edge.label', edges.length, numTypes);
                        }
                        let classes = 'e';
                        if (edgeInfos.some(({ edgeId }) => edgeId in focusing.edges)) {
                            classes += ' focus';
                        } else if (focusing.isFocusing) {
                            classes += ' focus-dim';
                        }

                        const edgeData = {
                            data: {
                                id: inNodeId + outNodeId,
                                source: outNodeId,
                                target: inNodeId,
                                label: multiEdgeLabel(edgesForInfos),
                                edges: edgesForInfos,
                                edgeInfos,
                            },
                            classes,
                            selected: _.any(edgeInfos, e => e.edgeId in edgesSelectedById)
                        };
                        return [...edgeGroups, edgeData];
                    } else {
                        return [...edgeGroups, ...edgeGroup];
                    }
                }, [])
                .map(data => {
                    if (data.id) {
                        const edgesForInfos = Object.values(_.pick(edges, _.pluck(data.edgeInfos, 'edgeId')));
                        return {
                            data: mapEdgeToData(data, edgesForInfos, relationships, registry['org.visallo.graph.edge.transformer']),
                            classes: mapEdgeToClasses(data.edgeInfos, edgesForInfos, focusing, registry['org.visallo.graph.edge.class']),
                            selected: _.any(data.edgeInfos, e => e.edgeId in edgesSelectedById)
                        }
                    } else {
                        return data;
                    }
                })
                .value();

            return { nodes: cyNodes, edges: cyEdges };

        },

        resetQueuedSelection(sel) {
            this._queuedSelection = sel ? {
                add: { vertices: { ...sel.vertices }, edges: { ...sel.edges }},
                remove: {vertices: {}, edges: {}}
            } : { add: {vertices: {}, edges: {}}, remove: {vertices: {}, edges: {}} };

            if (!this._queuedSelectionTrigger) {
                this._queuedSelectionTrigger = _.debounce(() => {
                    const vertices = Object.keys(this._queuedSelection.add.vertices);
                    const edges = Object.keys(this._queuedSelection.add.edges);
                    if (vertices.length || edges.length) {
                        this.props.onSetSelection({ vertices, edges })
                    } else {
                        this.props.onClearSelection();
                    }
                }, 100);
            }
        },

        coalesceSelection(action, type, cyElementOrId) {
            if (!this._queuedSelection) {
                this.resetQueuedSelection();
            }
            let id = cyElementOrId;

            if (cyElementOrId && _.isFunction(cyElementOrId.data)) {
                if (type === 'compoundNode') {
                    const vertexIds = cyElementOrId.data('vertexIds');
                    const edgeIds = _.values(this.props.product.extendedData.edges)
                        .filter(edge => vertexIds.includes(edge.inVertexId) && vertexIds.includes(edge.outVertexId))
                        .map(edge => edge.edgeId);

                    edgeIds.forEach(edgeId => {
                        this.coalesceSelection(action, 'edges', edgeId);
                    })
                    vertexIds.forEach(vertexId => {
                        this.coalesceSelection(action, 'vertices', vertexId);
                    });

                    return;
                } else if (type === 'edges') {
                    cyElementOrId.data('edgeInfos').forEach(edgeInfo => {
                        this.coalesceSelection(action, type, edgeInfo.edgeId);
                    });
                    return;
                } else if (type === 'vertices') {
                    id = cyElementOrId.id();
                } else {
                    console.error(`Invalid type: ${type}`);
                    return;
                }
            }

            if (action !== 'clear') {
                this._queuedSelection[action][type][id] = id;
            }

            if (action === 'add') {
                delete this._queuedSelection.remove[type][id]
            } else if (action === 'remove') {
                delete this._queuedSelection.add[type][id]
            } else if (action === 'clear') {
                this._queuedSelection.add.vertices = {};
                this._queuedSelection.add.edges = {};
                this._queuedSelection.remove.vertices = {};
                this._queuedSelection.remove.edges = {};
            } else {
                console.warn('Unknown action: ', action)
            }

            this._queuedSelectionTrigger();
        },

        showConnectionPopover() {
            const cy = this.cytoscape.state.cy;
            const { connectionType, vertexId, toVertexId, connectionData } = this.state.draw;
            const Popover = Popovers(connectionType);
            Popover.teardownAll();
            Popover.attachTo(this.node, {
                cy,
                cyNode: cy.getElementById(toVertexId),
                otherCyNode: cy.getElementById(vertexId),
                edge: cy.$('edge.drawEdgeToMouse'),
                outVertexId: vertexId,
                inVertexId: toVertexId,
                connectionData
            });
        },

        legacyListeners(map) {
            this.removeEvents = [];

            _.each(map, (handler, events) => {
                var node = this.node;
                var func = handler;
                if (!_.isFunction(handler)) {
                    node = handler.node;
                    func = handler.handler;
                }
                this.removeEvents.push({ node, func, events });
                $(node).on(events, func);
            })
        }
    });

    const getVertexIdsFromCollapsedNode = (collapsedNodes, collapsedNodeId) => {
        const vertexIds = [];
        const queue = [collapsedNodes[collapsedNodeId]];

        while (queue.length > 0) {
            const collapsedNode = queue.pop();
            collapsedNode.children.forEach(id => {
                if (collapsedNodes[id]) {
                    queue.push(collapsedNodes[id])
                } else {
                    vertexIds.push(id);
                }
            });
        }

        return vertexIds;
    };

    const mapEdgeToData = (data, edges, ontologyRelationships, transformers) => {
        const { id, edgeInfos, outNodeId, inNodeId } = data;

        return memoizeFor('org.visallo.graph.edge.transformer', edges, () => {
            const { label } = edgeInfos[0];
            const base = {
                id,
                source: outNodeId,
                target: inNodeId,
                type: label,
                label: edgeDisplay(label, ontologyRelationships, edgeInfos),
                edgeInfos,
                edges
            };

            if (edges.length) {
                return transformers.reduce((data, fn) => {

                    /**
                     * Mutate the object to change the edge data.
                     *
                     * @callback org.visallo.graph.edge.transformer~transformerFn
                     * @param {object} data The cytoscape data object
                     * @param {string} data.source The source vertex id
                     * @param {string} data.target The target vertex id
                     * @param {string} data.type The edge label IRI
                     * @param {string} data.label The edge label display value
                     * @param {array.<object>} data.edgeInfos
                     * @param {array.<object>} data.edges
                     * @example
                     * function transformer(data) {
                     *     data.myCustomAttr = '';
                     * }
                     */
                    fn(data)
                    return data;
                }, base)
            }

            return base;
        }, () => id + outNodeId + inNodeId)
    };

    const mapEdgeToClasses = (edgeInfos, edges, focusing, classers) => {
        let cls = [];
        if (edges.length) {

            /**
             * Mutate the classes array to adjust the classes.
             *
             * @callback org.visallo.graph.edge.class~classFn
             * @param {array.<object>} edges List of edges that are collapsed into the drawn line. `length >= 1`.
             * @param {string} type EdgeLabel of the collapsed edges.
             * @param {array.<string>} classes List of classes that will be added to cytoscape edge.
             * @example
             * function(edges, type, cls) {
             *     cls.push('org-example-cls');
             * }
             */

            cls = memoizeFor('org.visallo.graph.edge.class', edges, function() {
                const cls = [];
                classers.forEach(fn => fn(edges, edgeInfos.label, cls));
                cls.push('e');
                return cls;
            }, () => edges.map(e => e.id).sort())
        } else {
            cls.push('partial')
        }

        const classes = cls.join(' ');

        if (_.any(edgeInfos, info => info.edgeId in focusing.edges)) {
            return classes + ' focus';
        }
        if (focusing.isFocusing) {
            return classes + ' focus-dim'
        }
        return classes;
    };

    const decorationIdMap = {};

    const decorationForId = id => {
        return decorationIdMap[id];
    };

    const idForDecoration = (function() {
        const decorationIdCache = new WeakMap();
        const vertexIdCache = {};
        var decorationIdCacheInc = 0, vertexIdCacheInc = 0;
        return (decoration, vertexId) => {
            var id = decorationIdCache.get(decoration);
            if (!id) {
                id = decorationIdCacheInc++;
                decorationIdCache.set(decoration, id);
            }
            var vId;
            if (vertexId in vertexIdCache) {
                vId = vertexIdCache[vertexId];
            } else {
                vId = vertexIdCacheInc++;
                vertexIdCache[vertexId] = vId;
            }
            var full = `dec${vId}-${id}`;
            decorationIdMap[full] = decoration;
            return full;
        }
    })();
    const mapDecorationToData = (decoration, vertex, update) => {
        const getData = () => {
            var data;
            /**
             * _**Note:** This will be called for every vertex change event
             * (`verticesUpdated`). Cache/memoize the result if possible._
             *
             * @callback org.visallo.graph.node.decoration~data
             * @param {object} vertex
             * @returns {object} The cytoscape data object for a decoration
             * given a vertex
             */
            if (_.isFunction(decoration.data)) {
                data = decoration.data(vertex);
            } else if (decoration.data) {
                data = decoration.data;
            }
            if (!_.isObject(data)) {
                throw new Error('data is not an object', data)
            }
            var p = Promise.resolve(data);
            p.catch(e => console.error(e))
            if (_.isFunction(data.then)) {
                p.tap(result => {
                    update(result)
                });
            }
            return p;
        };
        const getIfFulfilled = p => {
            if (p.isFulfilled()) return p.value();
        }
        return getIfFulfilled(getData());
    };
    const mapDecorationToClasses = (decoration, vertex) => {
        var cls = ['decoration'];

        if (_.isString(decoration.classes)) {
            cls = cls.concat(decoration.classes.trim().split(/\s+/));
        } else if (_.isFunction(decoration.classes)) {

            /**
             * @callback org.visallo.graph.node.decoration~classes
             * @param {object} vertex
             * @returns {array.<string>|string} The classnames to add to the
             * node, either an array of classname strings, or space-separated
             * string
             */
            var newClasses = decoration.classes(vertex);
            if (!_.isArray(newClasses) && _.isString(newClasses)) {
                newClasses = newClasses.trim().split(/\s+/);
            }
            if (_.isArray(newClasses)) {
                cls = cls.concat(newClasses)
            }
        }
        return cls.join(' ');
    };

    const mapVertexToClasses = (id, vertices, focusing, hovering, classers) => {
        const vertexLoaded = (id in vertices);
        let cls = [];
        if (vertexLoaded) {
            const vertex = vertices[id];

            /**
             * Mutate the classes array to adjust the classes.
             *
             * @callback org.visallo.graph.node.class~classFn
             * @param {object} vertex The vertex that represents the node
             * @param {array.<string>} classes List of classes that will be added to cytoscape node.
             * @example
             * function(vertex, cls) {
             *     cls.push('org-example-cls');
             * }
             */
            cls = memoizeFor('org.visallo.graph.node.class', vertex, function() {
                const cls = [];
                classers.forEach(fn => fn(vertex, cls));
                cls.push('v');
                return cls;
            })
        } else {
            cls.push('partial')
        }

        const classes = cls.join(' ');
        if (id in focusing.vertices) {
            return classes + ' focus';
        }
        if (focusing.isFocusing) {
            return classes + ' focus-dim'
        }
        if (vertexLoaded && id === hovering) {
            return classes + ' fullTitle'
        }
        return classes;
    };

    const mapCollapsedNodeToClasses = (collapsedNodeId, collapsedNodes, focusing, vertexIds, hovering, classers) => {
        const loaded = (collapsedNodeId in collapsedNodes);
        const cls = [];
        if (loaded) {
            const collapsedNode = collapsedNodes[collapsedNodeId];

            /**
             * Mutate the classes array to adjust the classes.
             *
             * @callback org.visallo.graph.collapsed.class~classFn
             * @param {object} collapsedNode The collapsed item that represents the node
             * @param {array.<string>} classes List of classes that will be added to cytoscape node.
             * @example
             * function(collapsedNode, cls) {
             *     cls.push('org-example-cls');
             * }
             */
            classers.forEach(fn => fn(collapsedNode, cls));
            cls.push('c');

            if (vertexIds.some(vertexId => vertexId in focusing.vertices)) {
                cls.push('focus');
            } else if (focusing.isFocusing) {
                cls.push('focus-dim');
            }
        } else {
            cls.push('partial');
        }

        let classes = cls.join(' ');

        if (loaded && collapsedNodeId === hovering) {
            return classes + ' fullTitle';
        }

        return classes;
    };

    const getCyItemTypeAsString = (item) => {
        if (item.isNode()) {
            return item.data('vertexIds') ? 'compoundNode' : 'vertices';
        }
        return 'edges';
    };

    const generateCollapsedNodeTitle = (collapsedNode, vertices, productVertices, collapsedNodes) => {
        const children = _.chain(collapsedNode.children)
            .map(id => productVertices[id] || collapsedNodes[id])
            .compact()
            .reject(node => node.unauthorized || 'visible' in node && !node.visible)
            .value();
        const byType = _.groupBy(children, 'type');

        let title;
        if (vertices) {
            const { vertex } = byType;
            if (vertex && vertex.length > 1) {
                title = F.vertex.titles(vertex.reduce(function(list, { id }) {
                    if (vertices[id]) {
                        list.push(vertices[id])
                    }
                    return list;
                }, []), { maxBeforeOther: 3, maxTitleWords: 3 })
            } else {
                title = i18n('org.visallo.web.product.graph.collapsedNode.entities.singular');
            }
        }

        return title || i18n('org.visallo.web.product.graph.collapsedNode.entities', children.length);
    };

    const vertexToCyNode = (vertex, transformers) => {
        return memoizeFor('vertexToCyNode', vertex, function() {
            const title = F.string.truncate(F.vertex.title(vertex), MaxTitleWords);
            const conceptType = F.vertex.prop(vertex, 'conceptType');
            const imageSrc = F.vertex.image(vertex, null, 150);
            const selectedImageSrc = F.vertex.selectedImage(vertex, null, 150);
            const startingData = {
                id: vertex.id,
                title,
                conceptType,
                imageSrc,
                selectedImageSrc
            };

            return transformers.reduce((data, t) => {
                /**
                 * Mutate the data object that gets passed to Cytoscape.
                 *
                 * @callback org.visallo.graph.node.transformer~transformerFn
                 * @param {object} vertex The vertex representing this node
                 * @param {object} data The cytoscape data object
                 * @example
                 * function transformer(vertex, data) {
                 *     data.myCustomAttr = '...';
                 * }
                 */
                t(vertex, data)
                return data;
            }, startingData);
        });
    }

    const mapVertexToData = (id, vertices, transformers) => {
        if (id in vertices) {
            if (vertices[id] === null) {
                return;
            } else {
                const vertex = vertices[id];
                return vertexToCyNode(vertex, transformers);
            }
        } else {
            return { id }
        }
    };

    const CONFIGURATION = (props) => {
        const { pixelRatio, edgeLabels, product, registry } = props;
        const edgesCount = product.extendedData.edges.length;
        const styleExtensions = registry['org.visallo.graph.style'];

        return {
            minZoom: 1 / 16,
            maxZoom: 6,
            hideEdgesOnViewport: false,
            hideLabelsOnViewport: false,
            textureOnViewport: true,
            boxSelectionEnabled: true,
            panningEnabled: true,
            userPanningEnabled: true,
            zoomingEnabled: true,
            userZoomingEnabled: true,
            style: styleCache.getOrUpdate(styles, pixelRatio, edgesCount, edgeLabels, styleExtensions)
        }
    };

    return RegistryInjectorHOC(Graph, [
        'org.visallo.graph.edge.class',
        'org.visallo.graph.edge.transformer',
        'org.visallo.graph.export',
        'org.visallo.graph.node.class',
        'org.visallo.graph.node.decoration',
        'org.visallo.graph.node.transformer',
        'org.visallo.graph.ancillary',
        'org.visallo.graph.collapsed.class',
        'org.visallo.graph.selection',
        'org.visallo.graph.style',
        'org.visallo.graph.view'
    ]);
});
