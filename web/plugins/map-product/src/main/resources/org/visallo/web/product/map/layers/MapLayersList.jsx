define([
    'prop-types',
    'create-react-class',
    'react-virtualized',
    './SortableList',
    './MapLayerItem'
], function(
    PropTypes,
    createReactClass,
    { AutoSizer },
    SortableList,
    MapLayerItem) {

    const ROW_HEIGHT = 40;
    const SORT_DISTANCE_THRESHOLD = 10;

    const MapLayersList = ({ baseLayer, layers, editable, onOrderLayer, ...itemProps }) => (
        <div className="layer-list">
            {(baseLayer || layers.values) ?
                <div className="layers">
                    <div className="flex-fix">
                        <AutoSizer>
                            {({ width, height }) => ([
                                <SortableList
                                    ref={(instance) => { this.SortableList = instance; }}
                                    key={'sortable-items'}
                                    items={layers}
                                    shouldCancelStart={() => !editable}
                                    onSortStart={() => {
                                        this.SortableList.container.classList.add('sorting')
                                    }}
                                    onSortEnd={({ oldIndex, newIndex }) => {
                                        this.SortableList.container.classList.remove('sorting');

                                        if (oldIndex !== newIndex) {
                                            onOrderLayer(oldIndex, newIndex);
                                        }
                                    }}
                                    rowRenderer={mapLayerItemRenderer({ editable, ...itemProps })}
                                    rowHeight={ROW_HEIGHT}
                                    lockAxis={'y'}
                                    lockToContainerEdges={true}
                                    helperClass={'sorting'}
                                    distance={SORT_DISTANCE_THRESHOLD}
                                    width={width}
                                    height={(height - (ROW_HEIGHT + 1))}
                                />,
                                <SortableList
                                    key={'non-sortable-items'}
                                    className="unsortable"
                                    items={[ baseLayer ]}
                                    shouldCancelStart={() => true}
                                    rowRenderer={mapLayerItemRenderer({ editable, ...itemProps })}
                                    rowHeight={ROW_HEIGHT}
                                    width={width}
                                    height={ROW_HEIGHT}
                                />
                            ])}
                        </AutoSizer>
                    </div>
                </div>
            :
                <div className="empty">
                    <p>{ i18n('org.visallo.web.product.map.MapWorkProduct.layers.empty') }</p>
                </div>
            }
        </div>
    );

    MapLayersList.propTypes = {
        baseLayer: PropTypes.object,
        layers: PropTypes.array.isRequired,
        editable: PropTypes.bool,
        onOrderLayer: PropTypes.func.isRequired
    };

    const mapLayerItemRenderer = (itemProps) => (listProps) => {
        const { editable, ...rest } = itemProps;
        const { index, style, key, value: { config, layer }} = listProps;

        return (
            <MapLayerItem
                key={key}
                index={index}
                layer={layer}
                config={config}
                extension={'layer'}
                style={style}
                toggleable={editable}
                {...rest}
            />
        )
    };

    return MapLayersList;
});
