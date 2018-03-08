define([
    'prop-types',
    'create-react-class',
    'react-sortable-hoc',
    'react-virtualized'
], function(
    PropTypes,
    createReactClass,
    { SortableContainer, SortableElement, arrayMove },
    { List }) {

const VirtualList = createReactClass({

    propTypes: {
        items: PropTypes.array.isRequired,
        rowRenderer: PropTypes.func,
        className: PropTypes.string,
        rowHeight: PropTypes.number,
        width: PropTypes.number,
        height: PropTypes.number
    },

    render() {
        const { items, rowRenderer, className, rowHeight, width, height } = this.props;

        return (
            <List
                ref={(instance) => { this.List = instance; }}
                className={className}
                rowHeight={rowHeight}
                rowRenderer={({ index, ...rest }) => rowRenderer({ index, value: items[index], ...rest })}
                rowCount={items.length}
                width={width}
                height={height}
            />
        );
    }

});

return SortableContainer(VirtualList, { withRef: true });
});
