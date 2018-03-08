define([
    'create-react-class',
    'prop-types',
    'react-virtualized-select',
    'util/vertex/formatters'
], function(
    createReactClass,
    PropTypes,
    { default: VirtualizedSelect },
    F) {

    const splitUpString = str =>
        _.compact(F.string.normalizeAccents(str.toLowerCase())
            .replace(/[^a-zA-Z0-9]/g, ' ')
            .split(/\s+/)
        );

    const ElementSelector = createReactClass({
        propTypes: {
            onElementSelected: PropTypes.func,
            onCreateNewElement: PropTypes.func,
            searchOptions: PropTypes.object
        },

        getDefaultProps() {
            return { searchOptions: {} };
        },

        getInitialState() {
            return { options: [], isLoading: false };
        },

        componentDidMount() {
            this.onInputChange = _.debounce(this.onInputChange, 250);
            const { value } = this.props;
            if (value) {
                this.searchForElements(value, true)
            }
        },

        componentWillUnmount() {
            if (this.request) {
                this.request.cancel();
            }
        },

        render() {
            const { value: initialValue, creatable, ...rest } = this.props;
            const { value, options, isLoading } = this.state;
            const startIndex = creatable ? 1 : 0;

            return (
                <VirtualizedSelect
                    clearable
                    onChange={this.onChange}
                    onInputChange={this.onInputChange}
                    optionRenderer={ElementOptionRenderer}
                    valueRenderer={this.elementValueRenderer}
                    optionHeight={28}
                    labelKey="id"
                    valueKey="id"
                    placeholder={i18n('visallo.search')}
                    isLoading={isLoading}
                    filterOption={() => true}
                    options={options}
                    value={value || ''}
                    {...rest}
                />
            )
        },

        searchForElements(input, autoSelect = false) {
            const { searchOptions, filterResultsToTitleField } = this.props;
            const query = `${input}*`;

            if (!input.length) return;

            this.setState({ isLoading: true })
            return Promise.require('util/withDataRequest')
                .then(({ dataRequest }) => {
                    this.request = dataRequest('vertex', 'search', {
                        matchType: 'element',
                        paging: {
                            offset: 0,
                            size: 25
                        },
                        query,
                        ...searchOptions,
                        disableResultCache: true
                    });
                    return this.request;
                })
                .then(({ elements }) => {
                    let options = elements;

                    if (filterResultsToTitleField) {
                        const queryParts = splitUpString(query);
                        options = _.reject(options, function(v) {
                            var queryPartsMissingFromTitle = _.difference(
                                queryParts,
                                splitUpString(F.vertex.title(v))
                            ).length;
                            return queryPartsMissingFromTitle;
                        });
                    }
                    const { creatable, createNewRenderer, createNewLabel } = this.props;
                    if (creatable) {
                        options.splice(0, 0, {
                            id: '-1',
                            input,
                            creatable: true,
                            label: createNewRenderer ?
                                createNewRenderer(input) :
                                (createNewLabel || i18n('element.selector.create', input))
                        });
                    }

                    this.setState({ options, isLoading: false });

                    if (autoSelect) {
                        const startIndex = creatable ? 1 : 0;
                        let toSelect;

                        if (options.length > startIndex) {
                            toSelect = options[startIndex];
                        } else if (creatable) {
                            toSelect = options[0];
                        }

                        if (toSelect) {
                            this.onChange(toSelect);
                        }
                    }
                })
                .catch(error => {
                    console.error(error);
                    this.setState({ isLoading: false })
                })
        },

        onInputChange(input) {
            this.searchForElements(input)
        },

        onChange(option) {
            if (option) {
                this.setState({ value: option.id })
            } else {
                this.setState({ value: '' })
            }
            if (option && option.id === '-1') {
                if (this.props.onCreateNewElement) {
                    this.props.onCreateNewElement(option.input);
                }
            } else {
                if (this.props.onElementSelected) {
                    if (_.isEmpty(option)) {
                        this.props.onElementSelected()
                    } else {
                        this.props.onElementSelected(option)
                    }
                }
            }
        },

        elementValueRenderer(option) {
            const { creatable, input } = option;
            if (creatable) {
                const { createNewValueRenderer, createNewValueLabel } = this.props;
                return createNewValueRenderer ?
                    createNewValueRenderer(input) :
                    (createNewValueLabel || input);
            }
            return F.vertex.title(option);
        }
    });

    return ElementSelector;

    function ElementOptionRenderer({
        focusedOption, focusedOptionIndex, focusOption,
        key, labelKey,
        option, optionIndex, options,
        selectValue,
        style,
        valueArray
    }) {
        const className = ['VirtualizedSelectOption']
        if (option.className) {
            className.push(option.className);
        }
        if (option === focusedOption) {
            className.push('VirtualizedSelectFocusedOption')
        }
        if (option.disabled) {
            className.push('VirtualizedSelectDisabledOption')
        }
        if (option.header) {
            className.push('VirtualizedSelectHeader');
        }
        if (valueArray && valueArray.indexOf(option) >= 0) {
            className.push('VirtualizedSelectSelectedOption')
        }
        const events = option.disabled ? {} : {
            onClick: () => selectValue(option),
            onMouseOver: () => focusOption(option)
        };

        return (
            <div
                className={className.join(' ')}
                key={key}
                style={{ ...style }}
                title={option[labelKey]}
                {...events}>{option.creatable ? option.label : F.vertex.title(option)}</div>
        );
    }
});
