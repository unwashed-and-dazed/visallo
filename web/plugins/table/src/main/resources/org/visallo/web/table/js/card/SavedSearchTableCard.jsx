define([
    'create-react-class',
    'prop-types',
    './SavedSearchTable',
    '../table/columnConfigPopover'
], function(
    createReactClass,
    PropTypes,
    SavedSearchTable,
    columnConfigPopover) {
    'use strict';

    const PAGE_SIZE = 25;
    const COLUMN_WIDTH = 100;
    const DEFAULT_ROW_NUMBERS = true;
    const DEFAULT_TAB_SETTINGS = {
        direction: 'DESCENDING',
        sortPropertyIri: '',
        columns: [],
        active: false
    };
    const AGGREGATION_FIELD = {
        vertex: 'http://visallo.org#conceptType',
        edge: '__edgeLabel'
    };

    const legacyFormattersError = _.once(() => console.error('Legacy property formatters not supported in table'));

    const SavedSearchTableCard = createReactClass({
        propTypes: {
            item: PropTypes.object.isRequired,
            extension: PropTypes.object.isRequired,
            configurationChanged: PropTypes.func.isRequired,
            configureItem: PropTypes.func.isRequired,
            finishedLoading: PropTypes.func.isRequired,
            showError: PropTypes.func.isRequired,
            visalloApi: PropTypes.object.isRequired
        },

        getInitialState() {
            const { searchId, tableSettings } = this.props.item.configuration;

            return {
                tableData: {},
                tableView: searchId && tableSettings && tableSettings[searchId] || {},
                columnConfigure: false,
                previousRowClickIndex: 0
            };
        },

        componentWillMount() {
            this.dataRequest = this.props.visalloApi.v1.dataRequest;
        },

        componentDidMount() {
            const searchId = this.props.item.configuration.searchId;

            if (searchId) this.loadTable();

            this.onColumnResizeThrottled = _.throttle(this.onColumnResize, 1000 / 60);

            $(this.cardRef).on('click', (event) => {
                if (event.target.className.indexOf('FlexTable__row') > -1) {
                    event.stopPropagation();

                    const index = $(event.target).data('row-index');
                    this.onRowClick(event, index);
                }
            });
            $(this.cardRef).parent().on('refreshData', this.onRefreshData);
            $(this.cardRef).on('columnsConfigured', this.onColumnsConfigured);
            $(this.cardRef).on('sortConfigured', this.onSortConfigured);
        },

        componentWillUnmount() {
            $(this.cardRef).parent().off('refreshData', this.onRefreshData);
            $(this.cardRef).off('columnsConfigured', this.onColumnsConfigured);
            $(this.cardRef).off('sortConfigured', this.onSortConfigured);
        },

        componentWillReceiveProps(nextProps) {
            const { concepts: nextConcepts, relationships: nextRelationships, properties: nextProperties } = nextProps;
            const { concepts, relationships, properties } = this.props;

            if (concepts !== nextConcepts ||
                relationships !== nextRelationships ||
                properties !== nextProperties) {
                this.onRefreshData();
            }
        },

        render() {
            const { tableView, tableData } = this.state;
            const { configureItem, item } = this.props;
            const { searchId } = item.configuration;

            return (
                <div
                    className="com-visallo-table"
                    ref={(ref) => {this.cardRef = ref}}
                >
                    {(() => {
                        if (!searchId) {
                            return <a onClick={configureItem}>{i18n('com.visallo.table.config.search')}</a>
                        } else if (isLoading(searchId, tableView, tableData)) {
                            const message = isEmpty(searchId, tableData)
                                ? i18n('com.visallo.table.watermark.empty')
                                : i18n('com.visallo.table.watermark.loading');

                            return (
                                <div className="watermark">
                                    <h1>{message}</h1>
                                </div>
                            );
                        } else {
                            const dataProps = this.transformDataToProps();
                            return (
                                <SavedSearchTable
                                    {...dataProps}
                                    showRowNumbers={tableView.showRowNumbers}
                                    onRowsRendered={this.loadRows}
                                    onTabClick={this.switchToTab}
                                    onRowClick={this.onRowClick}
                                    onContextMenu={this.onContextMenu}
                                    onHeaderClick={this.onHeaderClick}
                                    onConfigureColumnsClick={this.toggleColumnConfigPopover}
                                    onColumnResize={this.onColumnResizeThrottled}
                                />
                            );
                        }
                    })()}
                </div>
            );

            function isLoading(searchId, tableView, tableData) {
                if (searchId) {
                    if (!tableData || !Object.keys(tableData).length) {
                        return true;
                    } else if (Object.keys(tableView).length <= 2) {
                        return true;
                    } else {
                        const activeTab = _.findKey(tableView, (tab) => tab.active || false);
                        return !tableData[searchId][activeTab];
                    }
                }
            }
        },

        transformDataToProps() {
            const { tableData, tableView } = this.state;
            const searchData = tableData[this.props.item.configuration.searchId];
            const activeTab = _.findKey(tableView, (tab) => tab.active || false);
            const selected = tableView.url.indexOf('vertex') > -1
                ? this.props.selection.vertices
                : this.props.selection.edges;
            const { sortColumn, direction } = tableView[activeTab];

            const data = {
                tabs: searchData.buckets,
                activeTab: activeTab,
                sort: { property: sortColumn, direction: direction },
                data: [ ...searchData[activeTab] ],
                columns: [ ...tableView[activeTab].columns ],
                selected: selected,
                scrollToTop: this.scrollToTop
            };

            if (this.scrollToTop) {
                this.scrollToTop = false;
            }

            return data;
        },

        loadTable() {
            const { item, extension, showError, configurationChanged } = this.props;
            const { searchId, tableSettings } = item.configuration;
            let tableView;

            return this.dataRequest('search', 'get', searchId)
                .catch((e) => {
                    throw new Error('Search unavailable');
                })
                .then((search) => {
                    const tableSearchSettings = {
                        showRowNumbers: DEFAULT_ROW_NUMBERS,
                        url: search.url
                    };

                    const configuration = {
                        ...item.configuration,
                        searchParameters: search.parameters
                    };
                    item.configuration = configuration;

                    if (tableSettings && tableSettings[searchId]) {
                        tableView = {
                            ...tableSettings[searchId],
                            url: search.url
                        };

                        if (_.isUndefined(tableView.showRowNumbers)) {
                            tableView.showRowNumbers = DEFAULT_ROW_NUMBERS;
                        }
                    } else if (tableSettings && search.parameters.conceptType && tableSettings[search.parameters.conceptType]) {
                        /**
                         * If there are no configuration for the saved search see if there are default configurations for this
                         * concept type. Note that if changes are made to the table, the configuration will be stored with
                         * the saved search configuration, not with the originating concept type configuration.
                         **/
                        tableView = {
                            ...tableSettings[search.parameters.conceptType],
                            url: search.url
                        };

                        if (_.isUndefined(tableView.showRowNumbers)) {
                            tableView.showRowNumbers = DEFAULT_ROW_NUMBERS;
                        }
                    } else {
                        tableView = tableSearchSettings;
                    }

                    this.updateTableView(tableView);
                })
                .then(this.loadTabs)
                .then((tabs) => {
                    if (Object.keys(tabs).length) {
                        tableView = this.updateTabsSettings(searchId, tabs);
                        let activeTab = _.findKey(tableView, (tab) => tab.active || false);

                        if (!activeTab) {
                            activeTab = Object.keys(tabs)[0];
                            tableView[activeTab].active = true;
                        }

                        this.updateTableView(tableView);
                    }
                })
                .catch((e) => {
                    if (e.message === 'Search unavailable') {
                        item.configuration = _.omit(item.configuration, 'searchId', 'searchParameters');

                        if (item.configuration.tableSettings) {
                            item.configuration.tableSettings = _.omit(item.configuration.tableSettings, searchId);
                        }

                        configurationChanged({
                            item: item,
                            extension: extension
                        });
                    } else {
                        console.warn(e);
                        showError();
                    }
                });
        },

        loadTabs() {
            const { tableData, tableView } = this.state;
            const { concepts, relationships } = this.props;
            const { searchId, searchParameters } = this.props.item.configuration;
            const url = tableView.url;
            const options = buildAggregateSearchOptions(url, searchParameters);

            return this.dataRequest('search', 'run', searchId, options)
                .then((results) => {
                    const buckets = _.mapObject(results.aggregates.field.buckets, (val, type) => {
                        const displayName = url.indexOf('vertex') > -1 ?
                            concepts[type].displayName :
                            relationships[type].displayName;

                        return {...val, displayName: displayName};
                    });

                    if (!tableData[searchId]) tableData[searchId] = {};
                    tableData[searchId].buckets = buckets;

                    _.mapObject(buckets, ({ count }, bucket) => {
                        const bucketMap = [];
                        for (let i = 0; i < count; i++) {
                            bucketMap[i] = null;
                        }

                        tableData[searchId][bucket] = bucketMap;
                    });

                    this.setState({ tableData: tableData });
                    return tableData[searchId].buckets;
                });

            function buildAggregateSearchOptions(searchUrl, searchParams) {
                const searchType = searchUrl.indexOf('vertex') > -1 ? 'vertex' : 'edge';
                const field = AGGREGATION_FIELD[searchType];
                const aggregations = [JSON.stringify({
                    type: 'term',
                    name: 'field',
                    field: field
                })];

                return { ...searchParams, size: 0, aggregations: aggregations };
            }
        },

        loadRows(startIndex, stopIndex) {
            const self = this;
            const { tableData, tableView } = this.state;
            const { item, showError } = this.props;
            const url = tableView.url;
            const { searchId, searchParameters} = item.configuration;
            const activeTab = _.findKey(tableView, (tab) => tab.active || false);
            const type = url.indexOf('vertex') > -1 ? 'vertex' : 'edge';

            if (isEmpty(searchId, tableData)) return;

            stopIndex += 1;
            stopIndex = Math.min(stopIndex, tableData[searchId].buckets[activeTab].count);

            const options = buildSearchOptionsFromTableView(searchParameters, url, tableView, activeTab, startIndex, stopIndex);

            updateRequested(startIndex, stopIndex);

            return this.dataRequest('search', 'run', searchId, options)
                .then(({ elements }) => transformResultsToRows(elements))
                .then((rows) => {
                    let rowIndex = 0;
                    for (let i = startIndex; i < stopIndex; i++) {
                        rows[rowIndex].index = i + 1;
                        tableData[searchId][activeTab][i] = rows[rowIndex];
                        rowIndex++;
                    }

                    this.setState({ tableData: tableData });
                })
                .catch((e) => {
                    console.warn(e);
                    showError();
                });

            function updateRequested(startIndex, stopIndex) {
                const requestRange = _.range(startIndex, stopIndex);

                requestRange.forEach((index) => {
                    tableData[searchId][activeTab][index] = 'loading';
                });
            }

            function buildSearchOptionsFromTableView(parameters, url, tableView, tabId, startIndex, stopIndex) {
                const tabSettings = tableView[tabId];
                const { direction, sortPropertyIri } = tabSettings;
                const sort = [`${sortPropertyIri}:${direction}`];
                const itemFilter = url.indexOf('vertex') > -1 ? 'conceptTypes' : 'edgeLabels';

                return {
                    ...parameters,
                    [itemFilter]: JSON.stringify([{ iri: tabId, includeChildNodes: false }]),
                    'sort[]': sort,
                    offset: startIndex,
                    size: stopIndex - startIndex
                };
            }

            function transformResultsToRows(results) {
                const ontologyProperties = self.props.properties;
                const F = self.props.visalloApi.v1.formatters;
                const columns = tableView[activeTab].columns;

                return Promise.map(results, (result) => {
                    return Promise.map(columns, ({ title }) => {
                        const values = [];
                        const ontologyProperty = ontologyProperties[title];
                        const isCompoundField = !!ontologyProperty.dependentPropertyIris;
                        const properties = _.uniq(F.vertex.props(result, title), (property) => (
                            isCompoundField ? property.key : (property.key + property.name)
                        ));
                        let propertiesPromise;

                        if (properties.length) {
                            propertiesPromise = Promise.all(getColumnValues(properties));
                        } else {
                            propertiesPromise = new Promise((fulfill) => {
                                values.push(' ');
                                fulfill();
                            });
                        }

                        return Promise.resolve(propertiesPromise)
                            .then(() => {
                                return [title, values];
                            });

                        function getColumnValues(properties) {
                            return Promise.map(properties, (p) => {
                                let displayValuePromise;
                                let wrapper = document.createElement('div')

                                if (p.name === 'http://visallo.org#title') {
                                    const options = {defaultValue: ' '};
                                    displayValuePromise = Promise.resolve(F[type].prop(result, title, p.key, options));
                                } else if (ontologyProperty.displayType && F[type].properties[ontologyProperty.displayType]) {
                                    displayValuePromise = Promise.resolve(F[type].properties[ontologyProperty.displayType](wrapper, p, result))
                                        .then((el) => {
                                            if (!el) {
                                                legacyFormattersError();
                                                return F[type].prop(result, title, p.key);
                                            } else {
                                                return el;
                                            }
                                        });
                                } else if (ontologyProperty.dataType && F[type].properties[ontologyProperty.dataType]) {
                                    displayValuePromise = Promise.resolve(F[type].properties[ontologyProperty.dataType](wrapper, p, result))
                                        .then((el) => {
                                            if (!el) {
                                                legacyFormattersError();
                                                return F[type].prop(result, title, p.key);
                                            } else {
                                                return el;
                                            }
                                    });
                                } else {
                                    displayValuePromise = Promise.resolve(F[type].prop(result, title, p.key));
                                }

                                return Promise.resolve(displayValuePromise)
                                    .then((displayValue) => {
                                        if (typeof displayValue !== 'object') {
                                            wrapper.textContent = displayValue;
                                            displayValue = wrapper;
                                        }

                                        values.push(displayValue);
                                    });
                            });

                        }
                    })
                    .then((pairs) => {
                        let dataProperties = _.object(pairs);
                        dataProperties.height = calculateRowHeight(dataProperties);
                        dataProperties.id = result.id;

                        return dataProperties;
                    });
                });
            }

            function calculateRowHeight(row) {
                const rowBasis = 35;
                if (_.isObject(row) && Object.keys(row).length) {
                    const mostProp = _.chain(row)
                        .values()
                        .map((propValues) => propValues.length)
                        .max()
                        .value();

                    return rowBasis * mostProp;
                } else {
                    return rowBasis;
                }
            }
        },

        onRefreshData() {
            this.setState({tableData: {}});
            this.loadTable();
        },

        updateTabsSettings(searchId, tabs) {
            const { tableView } = this.state;
            const { concepts, relationships } = this.props;

            const nextTableView = _.chain(tabs)
                .omit((tab, tabId) => !concepts[tabId] && !relationships[tabId])
                .mapObject(this.updateOrDefaultTabView)
                .value();
            nextTableView.url = tableView.url;
            nextTableView.showRowNumbers = tableView.showRowNumbers;

            this.updateTableView(nextTableView);

            return nextTableView;
        },

        updateOrDefaultTabView(tab, tabId) {
            const { tableView } = this.state;
            const { concepts, relationships, properties: ontologyProperties } = this.props;
            const properties = transformItemToTable(
                tabId,
                tableView.url.indexOf('vertex') > -1 ? 'vertex' : 'edge',
                { concepts, relationships, properties: ontologyProperties }
            );

            let tabView = { ...DEFAULT_TAB_SETTINGS };

            if (tableView[tabId]) {
                const { sortPropertyIri, direction, columns, active } = tableView[tabId];
                const nextColumns = properties.reduce((merged, p) => {
                    const column = columns.find(c => c.title === p.title);
                    merged.push(column || p);
                    return merged;
                }, []);

                tabView = {
                    ...tabView,
                    sortPropertyIri: properties[sortPropertyIri] ? sortPropertyIri : DEFAULT_TAB_SETTINGS.sortPropertyIri,
                    direction: properties[sortPropertyIri] ? direction : DEFAULT_TAB_SETTINGS.direction,
                    columns: nextColumns,
                    active

                };
            } else {
                tabView = {
                    ...tabView,
                    columns: properties
                };
            }

            return tabView;
        },

        updateTableView(tableView, shouldSave) {
            this.setState({ tableView: tableView });

            if (this.props.editable && shouldSave !== false) {
                const { item, extension, configurationChanged } = this.props;

                if (!item.configuration.tableSettings) {
                    item.configuration.tableSettings = {};
                }

                item.configuration.tableSettings[item.configuration.searchId] = tableView;

                configurationChanged({
                    item: item,
                    extension: extension,
                    recreate: false
                });
            }
        },

        switchToTab(tabId) {
            let { tableView } = this.state;
            tableView = _.mapObject(tableView, (val, key) => {
                if (_.isObject(val)) {
                    const active = key === tabId ? true : false;
                    return { ...val, active: active };
                }

                return val;
            });

            this.scrollToTop = true;
            this.updateTableView(tableView);
            _.defer(() => {this.loadRows(0, PAGE_SIZE)});
        },

        onRowClick(event, { index }) {
            const { tableData, tableView, previousRowClickIndex } = this.state;
            const { searchId } = this.props.item.configuration;
            const url = tableView.url;
            let selection = this.props.selection;
            const activeTab = _.findKey(tableView, (tab) => tab.active || false);
            const tabData = tableData[searchId][activeTab];
            const { vertices, edges } = selection;
            const currentSelection = {
                vertices: [...vertices],
                edges: [...edges]
            };
            const type = url.indexOf('vertex') > -1 ? 'vertices' : 'edges';
            let selectedIndicesForType = selection[type].map((id) => {
                return parseInt(_.findIndex(tabData, (value) => value && value.id === id));
            });

            if (isDiscontiguousSelectionKeyPressed(event)) {
                if (_.contains(selectedIndicesForType, index)) {
                   selectedIndicesForType = _.without(selectedIndicesForType, index);
                } else {
                    selectedIndicesForType.push(index);
                }

                currentSelection[type] = getIdsFromRows(tabData, selectedIndicesForType);
            } else if (event.shiftKey) {
                const min = Math.min(index, previousRowClickIndex);
                const max = Math.max(index, previousRowClickIndex);

                selectedIndicesForType = _.range(min, max + 1);
                currentSelection[type] = getIdsFromRows(tabData, selectedIndicesForType);
            } else {
                const id = tabData[index].id;
                currentSelection[type] = [id];
            }

            if (!event.shiftKey) {
                this.setState({ previousRowClickIndex: index });
            }

            this.props.onSetSelection(currentSelection);

            function isDiscontiguousSelectionKeyPressed(evt) {
                var isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;
                if (isMac) {
                    return evt.metaKey;
                } else {
                    return evt.ctrlKey;
                }
            }

            function getIdsFromRows(tabData, rowIndices) {
                const ids = [];
                _.forEach(rowIndices, (r) => {
                    ids.push(tabData[r].id);
                });

                return ids;
            }
        },

        onContextMenu(event, index) {
            event.nativeEvent.preventDefault();

            const { tableData, tableView } = this.state;
            const url = tableView.url;
            const { searchId } = this.props.item.configuration;
            const activeTab = _.findKey(tableView, (tab) => tab.active || false);
            const tabData = tableData[searchId][activeTab];

            const elementId = tabData[index].id;
            const position = { x: event.clientX, y: event.clientY };

            if (url.indexOf('vertex') > -1) {
                this.props.onVertexMenu(event.nativeEvent.target, elementId, position);
            } else {
                this.props.onEdgeMenu(event.nativeEvent.target, [elementId], position);
            }
        },

        onHeaderClick(header) {
            const { tableData, tableView } = this.state;
            const { searchId } = this.props.item.configuration;
            const activeTab = _.findKey(tableView, (tab) => tab.active || false);
            let tabSettings = tableView[activeTab];
            const column = tabSettings.columns.find(({ title }) => title === header);

            if (!column.sortPropertyIri) {
                column.sortPropertyIri = getSortPropertyIri(this.props.properties[header], header);
            }
            const sortPropertyIri = column.sortPropertyIri;
            const sortDirection = (!tabSettings.sortPropertyIri || tabSettings.direction === 'ASCENDING') ? 'DESCENDING' : 'ASCENDING';
            const isSortable = this.props.properties[sortPropertyIri].sortable !== false;

            if (!isSortable) return;

            tabSettings = {...tabSettings, sortPropertyIri: sortPropertyIri, sortColumn: header, direction: sortDirection};

            tableView[activeTab] = tabSettings;
            this.scrollToTop = true;
            this.updateTableView(tableView);

            tableData[searchId][activeTab] = tableData[searchId][activeTab].map((i) => null);
            this.setState({ tableData: tableData });
            this.loadRows(0, PAGE_SIZE);

            function getSortPropertyIri(ontologyProperty, title) {
                const isCompoundField = !!ontologyProperty.dependentPropertyIris;

                if (!isCompoundField) {
                    return title;
                } else {
                    return ontologyProperty.dependentPropertyIris[0];
                }
            }
        },

        onColumnResize(column, width, shouldSave) {
            const { tableView } = this.state;
            const activeTab = _.findKey(tableView, (tab) => tab.active || false);
            const tabSettings = tableView[activeTab];
            const columnIndex = _.findIndex(tabSettings.columns, ({ title }) => title === column);

            tableView[activeTab].columns[columnIndex].width = width;

            this.updateTableView(tableView, shouldSave);
        },

        toggleColumnConfigPopover(event) {
            const tableView = this.state.tableView;
            const properties = this.props.properties;
            const activeTab = _.findKey(tableView, (tab) => tab.active || false);
            const tabSettings = tableView[activeTab];
            const sortConfig = getSortConfiguration(properties, tabSettings);
            const node = event.target;
            const configPopover = $(node).lookupComponent(columnConfigPopover);

            if (configPopover) {
                return configPopover.teardown();
            } else {
                columnConfigPopover.attachTo(node, {
                    columns: tabSettings.columns,
                    showRowNumbers: tableView.showRowNumbers,
                    sortConfig: sortConfig
                });
            }

            function getSortConfiguration(ontologyProperties, tabSettings) {
                const column = tabSettings.sortColumn;

                if (column) {
                    const sortProperty = ontologyProperties[column];
                    const isCompoundProperty = !!sortProperty.dependentPropertyIris;
                    const sortableDependentProperties = isCompoundProperty ?
                        sortProperty.dependentPropertyIris.map((propertyIri) => {
                            const property = ontologyProperties[propertyIri];
                            if (property.userVisible === false || property.sortable === false) {
                                return null;
                            } else {
                                return {
                                    displayName: property.displayName,
                                    active: tabSettings.sortPropertyIri === propertyIri
                                }
                            }
                        }) : null;

                    return {
                        property: sortProperty.displayName,
                        isCompoundProperty: isCompoundProperty,
                        dependentProperties: sortableDependentProperties ? sortableDependentProperties.filter((p) => !!p) : null
                    };
                } else return {};
            }
        },

        onSortConfigured(event, data) {
            const { tableView, tableData } = this.state;
            const { searchId } = this.props.item.configuration;
            const activeTab = _.findKey(tableView, (tab) => tab.active || false);
            const tabSettings = tableView[activeTab];
            const sortColumn = getPropertyIri(data.sortColumn);
            const sortPropertyIri = getPropertyIri(data.propertyName);
            const columnIndex = _.findIndex(tabSettings.columns, ({ title }) => title === sortColumn);

            this.scrollToTop = true;
            tableView[activeTab].sortPropertyIri = tableView[activeTab].columns[columnIndex].sortPropertyIri = sortPropertyIri;
            this.updateTableView(tableView);

            tableData[searchId][activeTab] = tableData[searchId][activeTab].map((i) => null);
            this.setState({ tableData: tableData });
            this.loadRows(0, PAGE_SIZE);

            function getPropertyIri(displayName) {
                return this.props.properties.list.find((property) => property.displayName === displayName).title;
            }
        },

        onColumnsConfigured(event, data) {
            const { tableView } = this.state;
            const activeTab = _.findKey(tableView, (tab) => tab.active || false);
            const columns = tableView[activeTab].columns;
            const { selectedColumns, showRowNumbers } = data;

            if (!_.isUndefined(selectedColumns)) {
                const visibleColumns = [];
                const hiddenColumns = [];

                columns.map((column) => {
                    const index = _.indexOf(selectedColumns, column.title);

                    if (index > -1) {
                        visibleColumns[index] = {...column, visible: true};
                    } else {
                        hiddenColumns.push({...column, visible: false});
                    }
                });

                tableView[activeTab].columns = visibleColumns.concat(hiddenColumns);
            }

            if (!_.isUndefined(showRowNumbers)) {
                tableView.showRowNumbers = showRowNumbers
            }

            this.updateTableView(tableView);
        }
    });

    return SavedSearchTableCard;

    function transformItemToTable(iri, type, ontology) {
        const { concepts, relationships, properties: ontologyProperties } = ontology;
        const item = concepts[iri] || relationships[iri];
        let dependents = [];

        return _.chain(item.properties)
            .map((p) => {
                const property = ontologyProperties[p];
                if (property.dependentPropertyIris) {
                    dependents = dependents.concat(property.dependentPropertyIris)
                }
                return property;
            })
            .reject((p) => {
                return p.userVisible === false
                    || _.contains(dependents, p.title)
                    || p.displayType === 'longText';
            })
            .sortBy((p) => {
                if (p.title === 'http://visallo.org#title' || !p.displayName) {
                    return Number.MAX_VALUE;
                }
                return p.displayName;
            })
            .map(({ title, displayName }) => {
                return {
                    title: title,
                    displayName: displayName,
                    width: COLUMN_WIDTH,
                    visible: true
                };
            })
            .value();
    }

    function isEmpty(searchId, tableData) {
        if (searchId && tableData && tableData[searchId] && tableData[searchId].buckets !== undefined) {
            return !Object.keys(tableData[searchId].buckets).length;
        } else return false;
    }
});
