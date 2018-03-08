define([
    'create-react-class',
    'prop-types',
    'react-virtualized',
    'react-resizable',
    './SelectableRowRenderer'
], function(
    createReactClass,
    PropTypes,
    ReactVirtualized,
    ReactResizable,
    SelectableRowRenderer) {
    'use strict';

    const { AutoSizer, InfiniteLoader, Table: VirtualizedTable, Column } = ReactVirtualized;
    const { Resizable } = ReactResizable;

    const PAGE_SIZE = 25,
        HEADER_HEIGHT = 25,
        ROW_HEIGHT = 35,
        CONFIGURE_COLUMN_WIDTH = 35,
        HEADER_COLUMN_MARGIN = 10,
        HEADER_COLUMN_BORDER = 1,
        SCROLLBAR_WIDTH = 8;

    const Table = createReactClass({
        propTypes: {
            data: PropTypes.array.isRequired,
            columns: PropTypes.array.isRequired,
            selected: PropTypes.array,
            sort: PropTypes.object,
            scrollToTop: PropTypes.bool,
            showRowNumbers: PropTypes.bool,
            loadMoreRows: PropTypes.func,
            onHeaderClick: PropTypes.func,
            onRowClick: PropTypes.func,
            onContextMenu: PropTypes.func,
            onColumnResize: PropTypes.func,
            onConfigureClick: PropTypes.func
        },

        getInitialState() {
            return { scrollToTop: false }
        },

        componentWillReceiveProps(nextProps) {
            if (nextProps.scrollToTop && !this.state.scrollToTop) {
                this.setState({ scrollToTop: true });
            }
        },

        shouldComponentUpdate(nextProps, nextState) {
            if (this.props === nextProps && this.state.scrollToTop && !nextState.scrollToTop) {
                return false
            }
            return true;
        },

        componentWillUpdate(nextProps) {
            if (nextProps.showRowNumbers !== this.props.showRowNumbers) {
                this._VirtualizedTable.forceUpdateGrid();
            } else {
                this._VirtualizedTable.recomputeRowHeights();
            }
        },

        componentDidUpdate(prevProps, prevState) {
            if (this.state.scrollToTop) {
                this._VirtualizedTable.scrollToRow(0);
            }
        },

        render() {
            const {
                data,
                columns,
                selected,
                sort,
                showRowNumbers,
                onRowsRendered,
                onHeaderClick,
                onRowClick,
                onContextMenu,
                onColumnResize,
                onConfigureColumnsClick } = this.props;
            const rowCount = data.length;
            const tableWidth = columns.reduce((memo, { width, visible }) => {
                return visible ?
                    (memo + width + HEADER_COLUMN_MARGIN + HEADER_COLUMN_BORDER) : memo
                }, (HEADER_COLUMN_MARGIN + SCROLLBAR_WIDTH)) + CONFIGURE_COLUMN_WIDTH;

            return (
                <div className="table">
                    <InfiniteLoader
                        loadMoreRows={({ startIndex, stopIndex }) => onRowsRendered(startIndex, stopIndex)}
                        isRowLoaded={({ index }) => !!data[index]}
                        rowCount={rowCount}
                        minimumBatchSize={PAGE_SIZE}

                    >
                        {({ onRowsRendered, registerChild }) => (
                            <AutoSizer disableWidth={true}>
                                {({ height }) => (
                                    <VirtualizedTable
                                         overscanRowCount={0}
                                         width={tableWidth}
                                         height={height - 10}
                                         headerHeight={HEADER_HEIGHT}
                                         headerStyle={{marginRight: HEADER_COLUMN_MARGIN, borderRight: `${HEADER_COLUMN_BORDER}px solid white`}}
                                         rowClassName={({ index }) => !data[index] || data[index] === 'loading' ? 'loading' : ''}
                                         rowHeight={({ index }) => data[index] && data[index].height || ROW_HEIGHT}
                                         rowCount={rowCount}
                                         rowGetter={({ index }) => data[index] || {}}
                                         rowRenderer={(args) => SelectableRowRenderer({ ...args, selected, onContextMenu, onRowClick })}
                                         onScroll={this.onScroll}
                                         onRowsRendered={onRowsRendered}
                                         ref={(ref) => {
                                            this._VirtualizedTable = ref;
                                            registerChild(ref);
                                         }}
                                         sort={({ sortBy }) => {onHeaderClick(sortBy) }}
                                    >
                                        <Column
                                           label="Columns"
                                           dataKey="index"
                                           disableSort={true}
                                           width={CONFIGURE_COLUMN_WIDTH}
                                           flexGrow={0}
                                           flexShrink={0}
                                           key="configureColumns"
                                           headerRenderer={() => (
                                               <div
                                                   className="configure-column-header"
                                                   title={i18n('com.visallo.table.config.columns.hover')}
                                                   onClick={(event) => onConfigureColumnsClick(event)}
                                               ></div>
                                           )}
                                           style={{height: '100%'}}
                                           cellRenderer={({ key, style, cellData }) =>
                                               indexCellRenderer(key, style, cellData, showRowNumbers)
                                           }
                                        />
                                        {columns.map(({ displayName, title, visible, width: columnWidth }) => {
                                            if (visible) {
                                                return (
                                                    <Column
                                                       label={displayName}
                                                       dataKey={title}
                                                       width={columnWidth}
                                                       flexGrow={0}
                                                       flexShrink={0}
                                                       key={title}
                                                       headerRenderer={(opts) => resizableHeaderRenderer({
                                                              ...opts,
                                                              sort: sort,
                                                              headerWidth: columnWidth,
                                                              onHeaderResize:onColumnResize
                                                       })}
                                                       cellRenderer={(args) => cellRenderer(args, columnWidth)}
                                                       style={{height: '100%'}}
                                                    />
                                                );
                                            }
                                        })}

                                    </VirtualizedTable>
                                )}
                            </AutoSizer>
                        )}
                    </InfiniteLoader>
                </div>
            );
        },

        onScroll(data) {
            if (this.state.scrollToTop
                && this._VirtualizedTable
                && this._VirtualizedTable.Grid
                && this._VirtualizedTable.Grid.state
                && this._VirtualizedTable.Grid.state.scrollPositionChangeReason === 'observed') {
                this.setState({ scrollToTop: false });
            }
        }
    });

    return Table;

    function resizableHeaderRenderer({ dataKey, label, sort, headerWidth, onHeaderResize }) {
        const sortBy = sort.property === dataKey ? ' sort-' + sort.direction : '';

        return (
            <Resizable
                className="resizable-column-header"
                width={headerWidth}
                height={HEADER_HEIGHT}
                onResize={(event, { size }) => columnResize(size, false)}
                onResizeStop={(event, { size }) => columnResize(size, true)}
                onClick={(event) => {
                    if (event.target.className === 'react-resizable-handle') {
                        event.stopPropagation()
                    }
                }}
            >
                <div>
                    <span className={'ReactVirtualized__Table__headerTruncatedText' + sortBy} title={label}>{label}</span>
                </div>
            </Resizable>
        );

        function columnResize(size, shouldSave) {
            const { width } = size;
            onHeaderResize(dataKey, width, shouldSave);
        }
    }

    function cellRenderer({ key, style, dataKey, cellData }, width) {
        let i = 0;
        return (
            <div key={key} style={style}>
            {
                (cellData ?
                cellData.map((data) => (
                    <div
                        key={`${dataKey}(${i++}):${data}`}
                        className="property-value"
                        style={{width: width}}
                        dangerouslySetInnerHTML={{__html: data.outerHTML}}
                    ></div>
                )) : ' ')
            }
            </div>
        );
    }

    function indexCellRenderer(key, style, cellData, showRowNumbers) {
        return (
            <p
                className="property-value config-row-column"
                key={key}
                style={{...style, width: CONFIGURE_COLUMN_WIDTH}}
            >
                {showRowNumbers ? cellData : null}
            </p>
        );
    }
});
