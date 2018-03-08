define(['prop-types'], function(PropTypes) {
    'use strict';

    const ErrorFallbackComponent = ({ error }) => {
        let stack, message;

        if (_.isObject(error)) {
            stack = error.stack;
            message = error.message;
        } else {
            message = error;
        }

        return (
            <div className="ui-error">
                <div className="error-image"></div>
                <p className="error-text">{ i18n('visallo.default.ui.error.message') }</p>
                { visalloEnvironment.dev ?
                    <p className="error-dev" title={stack}>{ message }</p>
                : null }
            </div>
        );
    };

    ErrorFallbackComponent.propTypes = {
        error: PropTypes.oneOfType([
            PropTypes.string,
            PropTypes.object
        ])
    };

    return ErrorFallbackComponent;
});
