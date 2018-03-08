define([
    'create-react-class',
    'prop-types',
    'configuration/plugins/registry',
    'util/vertex/formatters',
    'util/withDataRequest'
], function(createReactClass, PropTypes, registry, F, withDataRequest) {
    'use strict';

    const DIVIDER = 'DIVIDER';

    const MENU_ITEM_SHAPE = PropTypes.shape({
        // function(currentSelection, elementId, DOMElement, element): return true if this item should be disabled
        shouldDisable: PropTypes.func,

        // function(currentSelection, element): return true if this item can handle the given vertex
        canHandle: PropTypes.func,

        // The primary label to display
        label: PropTypes.string.isRequired,

        // optional sub-menu
        submenu: PropTypes.arrayOf(PropTypes.any),

        // CSS class to apply
        cls: PropTypes.string,

        // Keyboard shortcut
        shortcut: PropTypes.string,

        // subtitle to display under the primary label
        subtitle: PropTypes.string,

        options: PropTypes.shape({
            insertIntoMenuItems: PropTypes.func
        })
    });

    const ElementContextMenuList = createReactClass({
        propTypes: {
            // the element that was clicked on
            element: PropTypes.any,

            // the collapsed item, vertex, or edge title
            elementTitle: PropTypes.string.isRequired,

            // the menu items to render
            items: PropTypes.arrayOf(
                PropTypes.oneOfType([
                    MENU_ITEM_SHAPE,
                    PropTypes.string
                ])
            ).isRequired,

            // the DOM element that originated the menu
            domElement: PropTypes.any.isRequired,

            // callback when a menu item is clicked
            onMenuItemClick: PropTypes.func.isRequired
        },

        handleMenuItemClick(item) {
            this.props.onMenuItemClick(item);
        },

        render() {
            const { items, element, elementTitle, domElement, onMenuItemClick } = this.props;

            return (
                <ul className="dropdown-menu" role="menu">
                    {this.props.items.map((item, itemIndex) => {
                        if (item === DIVIDER) {
                            return (<li key={itemIndex} className="divider"/>);
                        } else {
                            return (
                                <ElementContextMenuItem
                                    key={itemIndex}
                                    elementTitle={elementTitle}
                                    item={item}
                                    domElement={domElement}
                                    element={element}
                                    onClick={this.handleMenuItemClick.bind(this, item)}
                                    onMenuItemClick={onMenuItemClick}
                                />
                            );
                        }
                    })}
                </ul>
            );
        }
    });

    const ElementContextMenuItem = createReactClass({
        propTypes: {
            // the element that was clicked on
            element: PropTypes.any,

            // the collapsed item, vertex, or edge title
            elementTitle: PropTypes.string.isRequired,

            // the menu item to render
            item: MENU_ITEM_SHAPE,

            // the DOM element that originated the menu
            domElement: PropTypes.any.isRequired,

            // callback to call when this item is clicked
            onClick: PropTypes.func.isRequired,

            // callback when a menu item is clicked (needed to pass to submenus)
            onMenuItemClick: PropTypes.func.isRequired
        },

        getInitialState() {
            return {
                disabled: true
            };
        },

        componentWillMount() {
            const { element, domElement, item } = this.props;
            const currentSelection = visalloData.selectedObjects.vertexIds;
            const disabled = _.isFunction(item.shouldDisable)
                ? this.props.item.shouldDisable(currentSelection, element.id, domElement, element)
                : false;

            this.setState({
                disabled: disabled
            });
        },

        handleClick(event) {
            event.preventDefault();
            if (!this.state.disabled) {
                this.props.onClick();
            }
        },

        getLabel() {
            return _.template(this.props.item.label)({
                title: this.props.elementTitle
            });
        },

        hasSubmenu() {
            return this.props.item.submenu && this.props.item.submenu.length;
        },

        getClassName() {
            return (this.props.item.cls || '')
                + (this.hasSubmenu() ? ' dropdown-submenu' : '')
                + (this.state.disabled ? ' disabled' : '');
        },

        getLinkClassName() {
            return (this.props.item.shortcut ? 'has-shortcut' : '')
                + (this.props.item.subtitle ? ' has-subtitle' : '')
                + (this.hasSubmenu() ? ' has-submenu' : '');
        },

        renderShortcut() {
            const label = this.props.item.shortcut
                ? F.string.shortcut(this.props.item.shortcut)
                : null;
            if (label) {
                return (<span className="shortcut">{label}</span>);
            }
            return null;
        },

        renderSubtitle() {
            if (this.props.item.subtitle) {
                return (<span className="subtitle">{this.props.item.subtitle}</span>);
            }
        },

        renderSubmenu() {
            if (this.hasSubmenu()) {
                return (<ElementContextMenuList items={this.props.item.submenu} elementTitle={this.props.elementTitle}
                                                domElement={this.props.domElement} element={this.props.element}
                                                onMenuItemClick={this.props.onMenuItemClick}/>);
            }
            return null;
        },

        render() {
            return (
                <li className={this.getClassName()}>
                    <a className={this.getLinkClassName()} tabIndex="-1" href="#"
                       onClick={this.handleClick}>{this.getLabel()}
                        {this.hasSubmenu() ? '…' : ''}
                        {this.renderShortcut()}
                        {this.renderSubtitle()}
                    </a>
                    {this.renderSubmenu()}
                </li>
            );
        }
    });

    const ElementContextMenu = createReactClass({
        propTypes: {
            // can be a collapsedItemId, vertexId, or edgeIds
            vertexId: PropTypes.string,
            edgeIds: PropTypes.arrayOf(PropTypes.string),
            collapsedItemId: PropTypes.string,

            // the position to display the menu
            position: PropTypes.shape({
                x: PropTypes.number,
                y: PropTypes.number
            }).isRequired,

            // the DOM element that originated the menu
            domElement: PropTypes.any.isRequired
        },

        getInitialState() {
            return {
                items: [],
                title: 'Loading...'
            };
        },

        getElement() {
            if (this.props.collapsedItemId) {
                return Promise.resolve({
                    collapsedItemId: this.props.collapsedItemId
                });
            } else if (this.props.vertexId) {
                return withDataRequest.dataRequest('vertex', 'store', {vertexIds: this.props.vertexId});
            } else {
                return withDataRequest.dataRequest('edge', 'store', {edgeIds: this.props.edgeIds});
            }
        },

        componentWillMount() {
            const isCollapsedItem = !!this.props.collapsedItemId;
            const isVertex = !!this.props.vertexId;
            this.getElement()
                .then((elements) => {
                    if (!isCollapsedItem && (!elements || elements.length === 0)) {
                        throw new Error(`Could not find element: ${this.props.vertexId || this.props.edgeIds}`);
                    }

                    const items = isCollapsedItem
                        ? this.createCollapsedItemMenuItems()
                        : isVertex
                            ? this.createVertexMenuItems()
                            : this.createEdgeMenuItems();
                    const firstElement = _.isArray(elements)
                        ? elements[0]
                        : elements;
                    const currentSelection = isVertex
                        ? visalloData.selectedObjects.vertexIds
                        : visalloData.selectedObjects.edgeIds;

                    registry.extensionsForPoint(`org.visallo.${isCollapsedItem ? 'collapsedItem' : isVertex ? 'vertex' : 'edge'}.menu`)
                        .filter(item => {
                            if (item.selection) {
                                const amount = firstElement.id in currentSelection
                                    ? Object.keys(currentSelection).length
                                    : _.isArray(elements)
                                        ? elements.length
                                        : 1;
                                return item.selection === amount;
                            } else {
                                return true
                            }
                        })
                        .forEach(item => {
                            const canHandle = _.isFunction(item.canHandle) ? item.canHandle(currentSelection, elements) : true;

                            if (!canHandle) {
                                return;
                            }

                            if (item.options && _.isFunction(item.options.insertIntoMenuItems)) {
                                item.options.insertIntoMenuItems(item, items);
                            } else {
                                items.push(item);
                            }
                        });

                    const title = isCollapsedItem
                        ? i18n('vertex.contextmenu.collapsed')
                        : elements.length > 1
                            ? i18n('vertex.contextmenu.multiple')
                            : isVertex
                                ? F.string.truncate(F.vertex.title(firstElement), 3)
                                : F.string.truncate(F.edge.title(firstElement), 3);

                    this.setState({
                        domElement: this.props.domElement,
                        element: firstElement,
                        title: title,
                        items: items
                    });
                })
                .catch((err) => {
                    console.error('Could not get menu items', err);
                    this.setState({
                        domElement: this.props.domElement,
                        element: null,
                        title: 'Error',
                        items: []
                    });
                });
        },

        handleMenuItemClick(item) {
            $(this.state.domElement).trigger(
                item.event,
                {
                    ...item.args,
                    collapsedNodeId: this.props.collapsedItemId,
                    vertexId: this.props.vertexId,
                    edgeIds: this.props.edgeIds
                }
            );
        },

        positionMenu(position) {
            if (!this.refs.menuDiv) {
                return;
            }
            const padding = 10;
            const windowSize = {x: $(window).width(), y: $(window).height()};
            const menu = $(this.refs.menuDiv).children('.dropdown-menu');
            const menuSize = {x: menu.outerWidth(true), y: menu.outerHeight(true)};
            const submenu = menu.find('li.dropdown-submenu ul');
            const submenuSize = menuSize;
            const placement = {
                left: Math.min(
                    position.x,
                    windowSize.x - menuSize.x - padding
                ),
                top: Math.min(
                    position.y,
                    windowSize.y - menuSize.y - padding
                )
            };
            let submenuPlacement = {left: '100%', right: 'auto', top: 0, bottom: 'auto'};
            if ((placement.left + menuSize.x + submenuSize.x + padding) > windowSize.x) {
                submenuPlacement = $.extend(submenuPlacement, {right: '100%', left: 'auto'});
            }
            if ((placement.top + menuSize.y + (submenu.children('li').length * 26) + padding) > windowSize.y) {
                submenuPlacement = $.extend(submenuPlacement, {top: 'auto', bottom: '0'});
            }

            menu.parent('div')
                .addClass('open')
                .css($.extend({position: 'absolute'}, placement));
            submenu.css(submenuPlacement);
        },

        componentDidMount() {
            this.positionMenu(this.props.position);
        },

        componentDidUpdate() {
            this.positionMenu(this.props.position);
        },

        render() {
            const { items, title, element } = this.state;

            return (items.length ?
                <div className="vertex-menu" ref="menuDiv">
                    <ElementContextMenuList
                        items={items}
                        elementTitle={title}
                        domElement={this.props.domElement}
                        element={element}
                        onMenuItemClick={this.handleMenuItemClick}
                    />
                </div>
            : null);
        },

        createCollapsedItemMenuItems() {
            const items = [
                {
                    label: i18n('vertex.contextmenu.collapsed-node.rename'),
                    event: 'editCollapsedNode',
                    canHandle: () => visalloData.currentWorkspaceEditable
                },
                {
                    label: i18n('vertex.contextmenu.collapsed-node.select.contents'),
                    submenu: [
                        {
                            label: i18n('vertex.contextmenu.collapsed-node.select.all'),
                            event: 'selectCollapsedNodeContents',
                            args: { select: 'all' }
                        },
                        {
                            label: i18n('vertex.contextmenu.collapsed-node.select.none'),
                            event: 'selectCollapsedNodeContents',
                            args: { select: 'none' }
                        },
                        {
                            label: i18n('vertex.contextmenu.collapsed-node.select.vertices'),
                            event: 'selectCollapsedNodeContents',
                            args: { select: 'vertices' }
                        },
                        {
                            label: i18n('vertex.contextmenu.collapsed-node.select.edges'),
                            event: 'selectCollapsedNodeContents',
                            args: { select: 'edges' }
                        }
                    ]
                },
                DIVIDER,
                {
                    label: i18n('vertex.contextmenu.collapsed-node.uncollapse'),
                    event: 'uncollapse',
                    canHandle: () => visalloData.currentWorkspaceEditable
                }
            ];

            return items.filter(item => _.isFunction(item.canHandle) ? item.canHandle() : true);
        },

        createEdgeMenuItems() {
            return [
                {
                    label: i18n('vertex.contextmenu.open'),
                    submenu: [
                        {
                            label: i18n('vertex.contextmenu.open.fullscreen'),
                            subtitle: i18n('vertex.contextmenu.open.fullscreen.subtitle'),
                            event: 'openFullscreen'
                        }
                    ]
                }
            ];
        },

        createVertexMenuItems() {
            return [
                {
                    label: i18n('vertex.contextmenu.open'),
                    submenu: [
                        {
                            label: i18n('vertex.contextmenu.open.fullscreen'),
                            subtitle: i18n('vertex.contextmenu.open.fullscreen.subtitle'),
                            event: 'openFullscreen'
                        }
                    ]
                },
                {
                    label: i18n('vertex.contextmenu.select'),
                    submenu: [
                        {
                            label: i18n('vertex.contextmenu.select.connected'),
                            subtitle: i18n('vertex.contextmenu.select.connected.subtitle'),
                            shortcut: 'meta-e',
                            event: 'selectConnected'
                        }
                    ]
                },
                {
                    label: i18n('vertex.contextmenu.search'),
                    submenu: [
                        {
                            label: '{ title }',
                            shortcut: 'alt+t',
                            event: 'searchTitle',
                            selection: 1
                        },
                        {
                            label: i18n('graph.contextmenu.search.related'),
                            subtitle: i18n('graph.contextmenu.search.related.subtitle'),
                            shortcut: 'alt+s',
                            event: 'searchRelated',
                            selection: 1
                        }
                    ]
                },
                DIVIDER,
                {
                    label: i18n('vertex.contextmenu.remove'),
                    shortcut: 'delete',
                    subtitle: i18n('vertex.contextmenu.remove.subtitle'),
                    event: 'deleteSelected',
                    shouldDisable: function(selection, vertexId, target) {
                        return !visalloData.currentWorkspaceEditable || false;
                        // TODO:  !inWorkspace(vertexId);
                    }
                }
            ];
        }
    });

    return ElementContextMenu;
});
