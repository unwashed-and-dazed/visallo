define([
    'create-react-class',
    'prop-types',
    'react-redux',
    'data/web-worker/store/user/selectors',
    '../Attacher',
    '../RegistryInjectorHOC'
], function(createReactClass, PropTypes, redux, userSelectors, Attacher, RegistryInjectorHOC) {
    'use strict';

    const DEFAULT_FLIGHT_EDITOR = 'util/visibility/default/edit';

    const VisibilityEditor = createReactClass({
        propTypes: {
            onVisibilityChanged: PropTypes.func
        },
        getDefaultProps() {
            return { value: '', placeholder: i18n('visibility.label') }
        },
        getInitialState() {
            return { value: this.props.value, valid: true }
        },
        componentWillReceiveProps({ value }) {
            if (value !== this.state.value) {
                this.setState({ value, valid: this.checkValid(value) })
            }
        },
        render() {
            const { registry, style, value: oldValue, placeholder, ...rest } = this.props;
            const { value, valid } = this.state;
            const custom = _.first(registry['org.visallo.visibility']);

            // Use new react visibility renderer as default if no custom exists
            if (custom && custom.editorComponentPath !== DEFAULT_FLIGHT_EDITOR) {
                return (
                    <Attacher
                        value={value}
                        placeholder={placeholder}
                        componentPath={custom.editorComponentPath}
                        {...rest} />
                );
            }

            return (
                <input
                    type="text"
                    onChange={this.onChange}
                    value={value}
                    placeholder={placeholder}
                    className={valid ? '' : 'invalid'} />
            )
        },
        onChange(event) {
            const value = event.target.value;
            const valid = this.checkValid(value)
            this.setState({ value, valid })
            this.props.onVisibilityChanged({ value, valid })
        },
        checkValid(value) {
            var authorizations = this.props.authorizations;
            return Boolean(!value.length || value in authorizations);
        }
    });

    return redux.connect(
        (state, props) => {
            return {
                authorizations: userSelectors.getAuthorizations(state),
                ...props
            };
        },

        (dispatch, props) => ({
        })
    )(RegistryInjectorHOC(VisibilityEditor, [
        'org.visallo.visibility'
    ]));
});
