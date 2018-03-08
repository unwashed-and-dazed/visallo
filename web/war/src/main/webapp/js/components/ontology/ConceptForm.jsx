define([
    'create-react-class',
    'classnames',
    'prop-types',
    './ConceptSelector',
    '../field/GlyphSelector',
    '../field/ColorSelector',
    '../Alert'
], function(createReactClass,
    classNames,
    PropTypes,
    ConceptsSelector,
    GlyphSelector,
    ColorSelector,
    Alert) {

    const ConceptForm = createReactClass({
        propTypes: {
            transformForSubmit: PropTypes.func.isRequired,
            transformForInput: PropTypes.func.isRequired,
            onCreate: PropTypes.func.isRequired,
            onCancel: PropTypes.func.isRequired,
            displayName: PropTypes.string
        },
        getInitialState() {
            return {}
        },
        getValue() {
            const { displayName } = this.state;
            const { displayName: defaultValue } = this.props;
            return _.isString(displayName) ? displayName : defaultValue;
        },
        render() {
            const { transformForSubmit, transformForInput } = this.props;
            const { color } = this.state;
            const value = this.getValue();
            const valueForInput = transformForInput(value);
            const { valid, reason, value: valueForSubmit } = transformForSubmit(value);
            const disabled = !valid;
            return (
                <div className="ontology-form">
                    { this.props.error ? (<Alert error={this.props.error} />) : null }
                    <input type="text"
                        placeholder={i18n('ontology.form.displayname.placeholder')}
                        onChange={this.onDisplayNameChange}
                        title={reason}
                        className={classNames({ invalid: !valid })}
                        value={valueForInput} />

                    <ConceptsSelector
                        value={this.state.parentConcept}
                        placeholder={i18n('ontology.concept.inherit.placeholder')}
                        creatable={false}
                        onSelected={this.onConceptSelected} />

                    <ColorSelector value={color} onSelected={this.onColorSelected} />
                    <GlyphSelector
                        placeholder={i18n('ontology.concept.icon.placeholder')}
                        search={value} onSelected={this.onIconSelected} />

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
        onColorSelected(color) {
            this.setState({ color })
        },
        onIconSelected(imgSrc) {
            this.setState({ imgSrc })
        },
        onConceptSelected(option) {
            const newState = { parentConcept: null, color: null };
            if (option) {
                newState.color = option.color;
                newState.parentConcept = option.title;
            }

            this.setState(newState);
        },
        onDisplayNameChange(event) {
            this.setState({ displayName: event.target.value })
        },
        onCreate() {
            const { parentConcept, color, imgSrc } = this.state;
            this.props.onCreate({
                parentConcept: parentConcept,
                displayName: this.getValue(),
                glyphIconHref: imgSrc,
                color: color || 'rgb(0,0,0)'
            })
        }
    });

    return ConceptForm;
});
