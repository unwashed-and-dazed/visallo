define([
    'create-react-class',
    'prop-types',
    'classnames',
    './ConceptSelector',
    './RelationshipSelector',
    '../Alert'
], function(
    createReactClass,
    PropTypes,
    classNames,
    ConceptsSelector,
    RelationshipSelector,
    Alert) {

    const DataTypes = [
        {
            label: i18n('ontology.property.dataformat.text'),
            options: [
                { type: 'string', label: i18n('ontology.property.dataformat.text.string') },
                { type: 'string', displayType: 'link', label: i18n('ontology.property.dataformat.text.link') }
            ]
        },
        {
            label: i18n('ontology.property.dataformat.number'),
            options: [
                { type: 'integer', label: i18n('ontology.property.dataformat.number.integer') },
                { type: 'double', label: i18n('ontology.property.dataformat.number.double') },
                { type: 'currency', label: i18n('ontology.property.dataformat.number.currency') },
                { type: 'double', displayType: 'duration', label: i18n('ontology.property.dataformat.number.duration') },
                { type: 'integer', displayType: 'bytes', label: i18n('ontology.property.dataformat.number.bytes') }
            ]
        },
        {
            label: i18n('ontology.property.dataformat.date'),
            options: [
                { type: 'date', label: i18n('ontology.property.dataformat.date.date') },
                { type: 'date', displayType: 'dateOnly', label: i18n('ontology.property.dataformat.date.dateOnly') }
            ]
        },
        {
            label: i18n('ontology.property.dataformat.location'),
            options: [
                { type: 'geoLocation', label: i18n('ontology.property.dataformat.location.geoLocation') }
            ]
        }
    ];
    const transformOptions = dataTypes => {
        if (_.isArray(dataTypes) && dataTypes.length) {
            const filtered = DataTypes.map(group => {
                return { ...group, options: group.options.filter(option => dataTypes.includes(option.type))}
            })
            return filtered.filter(group => group.options.length)
        }
        return DataTypes;
    }
    const DataTypeSelect = function(props) {
        const { type, dataTypes, ...rest } = props;
        const groups = transformOptions(dataTypes)

        return (
            <select value={type || ''} {...rest}>
                <option value="">{i18n('ontology.property.dataformat.placeholder')}</option>
                {
                    groups.map(group => (
                        <optgroup key={group.label} label={group.label}>
                            {
                                group.options.map(option => {
                                    const { type, displayType, label } = option;
                                    const combined = _.compact([type, displayType]).join('|');
                                    return (
                                        <option key={combined} value={combined}>{label}</option>
                                    )
                                })
                            }
                        </optgroup>
                    ))
                }
            </select>
        )
    }
    const PropertyForm = createReactClass({
        propTypes: {
            transformForSubmit: PropTypes.func.isRequired,
            transformForInput: PropTypes.func.isRequired,
            onCreate: PropTypes.func.isRequired,
            onCancel: PropTypes.func.isRequired,
            displayName: PropTypes.string,
            domain: PropTypes.string,
            type: PropTypes.string,
            dataType: PropTypes.string,
            dataTypes: PropTypes.arrayOf(PropTypes.string)
        },
        getInitialState() {
            return {};
        },
        getValue() {
            const { displayName } = this.state;
            const { displayName: defaultValue } = this.props;
            return _.isString(displayName) ? displayName : defaultValue;
        },
        componentDidMount() {
            const { domain, type } = this.props;
            this.setState({ domain, type })
        },
        componentWillReceiveProps(nextProps) {
            if (nextProps.domain !== this.state.domain) {
                this.setState({ domain: this.props.domain })
            }
            if (nextProps.type !== this.state.type) {
                this.setState({ type: nextProps.type })
            }
        },
        render() {
            const { domain, type } = this.state;
            const { conceptId, relationshipId, dataType, dataTypes, error, transformForSubmit, transformForInput } = this.props;
            const value = this.getValue();
            const valueForInput = transformForInput(value);
            const { valid, reason, value: valueForSubmit } = transformForSubmit(value);
            const disabled = !valid || !type || !domain;
            const filterDataTypes = dataTypes ? dataTypes : dataType ? [dataType] : null;

            return (
                <div className="ontology-form">
                    { error ? (<Alert error={error} />) : null }
                    <input type="text"
                        placeholder={i18n('ontology.form.displayname.placeholder')}
                        onChange={this.onDisplayNameChange}
                        title={reason}
                        className={classNames({ invalid: !valid })}
                        value={valueForInput} />

                    { relationshipId ?
                        (<RelationshipSelector
                            value={domain}
                            creatable={false}
                            clearable={false}
                            filter={{ relationshipId, showAncestors: true }}
                            onSelected={this.onDomainSelected} />) :
                        (<ConceptsSelector
                            value={domain}
                            creatable={false}
                            clearable={false}
                            filter={{ conceptId, showAncestors: true }}
                            onSelected={this.onDomainSelected} />)
                    }

                    <DataTypeSelect type={type} dataTypes={filterDataTypes} onChange={this.handleTypeChange} />

                    <div className="base-select-form-buttons">
                        <button onClick={this.props.onCancel}
                            className="btn btn-link btn-small">{i18n('ontology.form.cancel.button')}</button>
                        <button disabled={disabled} onClick={this.onCreate}
                            className="btn btn-small btn-primary">{
                                disabled ?
                                    i18n('ontology.form.create.button') :
                                    i18n('ontology.form.create.value.button', valueForSubmit)
                            }</button>
                    </div>
                </div>
            )
        },
        onDomainSelected(option) {
            this.setState({ domain: option ? option.title : null })
        },
        onDisplayNameChange(event) {
            this.setState({ displayName: event.target.value })
        },
        handleTypeChange(event) {
            this.setState({ type: event.target.value });
        },
        onCreate() {
            const domain = {};
            if (this.props.relationshipId) {
                domain.relationshipIris = [this.state.domain];
            } else {
                domain.conceptIris = [this.state.domain];
            }
            const [dataType, displayType] = this.state.type.split('|');

            this.props.onCreate({
                domain,
                dataType,
                displayType,
                displayName: this.getValue()
            })
        }
    });

    return PropertyForm;
});
