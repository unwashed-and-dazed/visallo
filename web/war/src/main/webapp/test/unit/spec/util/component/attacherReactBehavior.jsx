define([
    'create-react-class',
    'prop-types'
], function(createReactClass, PropTypes) {
    'use strict';

    const ReactParams = createReactClass({
        propTypes: {
            visalloApi: PropTypes.object.isRequired,
            customBehavior: PropTypes.func.isRequired
        },
        componentDidMount() {
            setTimeout(() => {
                this.props.customBehavior('param1')
            }, 0)
        },
        render: function(){
            const { customBehavior } = this.props;
            return (
                <div>Behavior Test</div>
            );
        }
    })

    return ReactParams;
});
