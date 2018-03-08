define([
    'prop-types',
    '../table/Tabs',
    '../table/Table'
], function(
    PropTypes,
    Tabs,
    Table) {
    'use strict';

    const SavedSearchTable = ({ tabs, activeTab, onTabClick, ...tableProps }) => {
        return (
            <div className="saved-search-table">
                <Tabs
                    tabs={tabs}
                    activeTab={activeTab}
                    onTabClick={onTabClick}
                />
                <Table
                    activeTab={activeTab}
                    {...tableProps}
                />
            </div>
        );
    };

    SavedSearchTable.propTypes = {
        data: PropTypes.array.isRequired,
        columns: PropTypes.array.isRequired,
        tabs: PropTypes.object.isRequired,
        activeTab: PropTypes.string,
        sort: PropTypes.object,
        scrollToTop: PropTypes.bool,
        selected: PropTypes.array,
        showRowNumbers: PropTypes.bool,
        onRowsRendered: PropTypes.func,
        onTabClick: PropTypes.func,
        onHeaderClick: PropTypes.func,
        onRowClick: PropTypes.func,
        onContextMenu: PropTypes.func,
        onColumnResize: PropTypes.func,
        onConfigureColumnsClick: PropTypes.func
    };

    return SavedSearchTable;
});
