define([
    'create-react-class',
    'prop-types',
    'classnames'
], function(createReactClass, PropTypes, classNames) {
    'use strict';

    const NumberRange = createReactClass({
        propTypes: {
            value: PropTypes.number,
            min: PropTypes.number,
            max: PropTypes.number,
            step: PropTypes.number,
            editable: PropTypes.bool,
            displayTooltip: PropTypes.bool,
            labelRenderer: PropTypes.func,
            onChange: PropTypes.func.isRequired,
        },

        getDefaultProps() {
            return {
                min: 0,
                max: 1,
                step: 0.1,
                editable: true,
                displayTooltip: true,
                labelRenderer: value => value
            };
        },

        render() {
            const { value, min, max, step, editable, displayTooltip, labelRenderer, onChange } = this.props;
            const percent = calculatePercent(min, max, value);
            const hasValue = value !== undefined && value !== null;

            return (
                <div className={classNames('number-range-wrapper', { 'empty': !hasValue })}>
                    <input
                        ref={r => { this.input = r }}
                        className="number-range-input"
                        type="range"
                        disabled={!editable}
                        min={min}
                        max={max}
                        step={step}
                        defaultValue={value}
                        onChange={(e) => { onChange(Number.parseFloat(this.input.value)); }}
                    />
                    {displayTooltip ?
                        <div className="tooltip bottom" style={{
                            left: percent * 100 + '%',
                            marginLeft: ((1 - percent) * (25 * 2) - 25) + 'px',
                            transform: 'translate(-50%, 0px)'
                        }}>
                            <div className="tooltip-arrow"></div>
                            <div style={{ background: 'black' }} className="tooltip-inner">
                                { hasValue ? labelRenderer(value) : null }
                            </div>
                        </div>
                    : null}
                </div>
            );
        }

    });

    function calculatePercent(min, max, value) {
        if (value !== null && value !== undefined) {
            const range = Math.abs(max - min);
            const rangeValue = max > min ? Math.abs(value - min) : Math.abs(min - value);
            return rangeValue / range;
        } else {
            return 1;
        }
    }

    return NumberRange;
});
