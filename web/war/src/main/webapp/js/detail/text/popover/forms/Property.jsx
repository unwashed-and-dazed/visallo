define([
    'create-react-class',
    'prop-types',
    'components/Attacher'
], function(
    createReactClass,
    PropTypes,
    Attacher) {

    const Property = createReactClass({
        propTypes: {
            onCancel: PropTypes.func.isRequired,
            onSave: PropTypes.func.isRequired,
            attemptToCoerceValue: PropTypes.string,
            sourceInfo: PropTypes.object
        },

        getInitialState() {
            return {};
        },

        shouldComponentUpdate(nextProps, nextState) {
            if (this._ref && nextProps.error && nextProps.error !== this.props.error) {
                const $node = $(this._ref.attacher._node)
                $node.trigger('propertyerror', { error: nextProps.error })
            }

            // Work around flight component not handling state changes well
            return false;
        },

        render() {
            const { onCancel, onSave, attemptToCoerceValue, sourceInfo } = this.props;
            const { element, property } = this.state;

            return (
                <div className="form" style={{padding: 0}}>
                    <h1 style={{marginBottom: '-0.3em', padding: '0.5em 1em 0'}}>
                        { i18n('detail.text.terms.form.resolve.property') }
                    </h1>
                    <Attacher
                        componentPath="detail/dropdowns/propertyForm/propForm"
                        behavior={{
                            propFormPropertyChanged: (inst, { property }) => {
                                this.setState({ property })
                            },
                            propFormVertexChanged: (inst, { vertex }) => {
                                this.setState({ element: vertex })
                            },
                            addProperty: (inst, { node, ...data }) => {
                                const { element, property } = data;
                                onSave({ element, property })
                            },
                            closeDropdown: () => {
                                onCancel();
                            }
                        }}
                        ref={r => {this._ref = r}}
                        data={element}
                        property={property}
                        allowDeleteProperty={false}
                        allowEditProperty={false}
                        disableDropdownFeatures={true}
                        attemptToCoerceValue={attemptToCoerceValue}
                        sourceInfo={sourceInfo}
                    />
                </div>
            )
        }
    });

    return Property;
});
