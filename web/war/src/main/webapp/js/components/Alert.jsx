define([
    'create-react-class', 'prop-types'
], function(createReactClass, PropTypes) {
    'use strict';

    const Alert = createReactClass({
        propTypes: {
            error: PropTypes.any,
            onDismiss: PropTypes.func
        },

        componentWillReceiveProps(nextProps) {
            if (nextProps.error && nextProps.error !== this.props.error) {
                console.error(nextProps.error);
            }
        },

        renderMessage() {
            const { error } = this.props;

            var info;
            if (_.isString(error)) {
                info = error;
            } else if (error.statusText) {
                info = error.statusText;
            } else {
                info = i18n('admin.plugin.error');
            }

            if (_.isArray(info) && info.length > 1) {
                return (
                    <ul>
                        {info.map((i)=> {
                            return (<li>{i}</li>);
                        })}
                    </ul>
                )
            } else if (_.isArray(info)) {
                return (<div>{info[0]}</div>);
            } else {
                return (<div>{info}</div>);
            }
        },

        renderType() {
            if (this.props.error.type) {
                return (<strong>{this.props.error.type}</strong>);
            }
            return null;
        },

        handleDismissClick(e) {
            if (this.props.onDismiss) {
                this.props.onDismiss(e);
            }
        },

        render() {
            if (!this.props.error) {
                return null;
            }

            return (
                <div className="alert alert-error">
                    {this.props.onDismiss ? (
                        <button type="button" className="close" onClick={this.handleDismissClick}>&times;</button>
                    ) : null}
                    {this.renderType()}
                    {this.renderMessage()}
                </div>
            );
        }
    });

    return Alert;
});
