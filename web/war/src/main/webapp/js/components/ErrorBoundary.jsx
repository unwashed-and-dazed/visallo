define([
    'react',
    'create-react-class',
    'prop-types',
    './ErrorFallbackComponent'
], function(React, createReactClass, PropTypes, ErrorFallbackComponent) {
    'use strict';

    const ErrorBoundary = createReactClass({
        propTypes: {
            onError: PropTypes.func,
            FallbackComponent: PropTypes.func
        },

        getInitialState() {
            return { error: null };
        },

        componentDidCatch(error, info) {
            this.setState({ error });

            if (_.isFunction(this.props.onError)) {
                this.props.onError(error)
            }
        },

        render() {
            const { error } = this.state;
            const { FallbackComponent, children, ...rest } = this.props;

            if (error && _.isFunction(FallbackComponent)) {
                return <FallbackComponent error={error} {...rest} />
            } else if (error) {
                return <ErrorFallbackComponent error={error} />
            } else {
                return children;
            }
        }
    });

    return ErrorBoundary;
});
