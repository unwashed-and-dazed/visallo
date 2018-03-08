define([
    'create-react-class'
], function(createReactClass) {
    'use strict';

    const ReactReattach = createReactClass({
        componentDidMount() {
            setTimeout(() => {
                this.props.changeParam('changed')
            }, 0)
        },
        render: function(){
            return (
              <div>{this.props.param}</div>
            );
        }
    })

    return ReactReattach;
});
