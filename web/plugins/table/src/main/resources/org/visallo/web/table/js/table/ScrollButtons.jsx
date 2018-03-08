define([
    'prop-types',
    'classnames'
], function(
    PropTypes,
    classNames) {
    'use strict';

    const ScrollButtons = ({ offset, overflow, onScrollClick }) => {
        const buttonClass = ['scrollButton', 'disable-text-selection'];

        return (
            <div className="tabScrollButtons">
                <div className={classNames(buttonClass, { disabled: offset === 0 })} onClick={() => onScrollClick('left')}> ◀ </div>
                <div className={classNames(buttonClass, { disabled: !overflow })} onClick={() => onScrollClick('right')}> ▶ </div>
            </div>
        );
    };

    ScrollButtons.propTypes = {
        offset: PropTypes.number,
        overflow: PropTypes.bool,
        onScrollClick: PropTypes.func
    };

    return ScrollButtons;
});
