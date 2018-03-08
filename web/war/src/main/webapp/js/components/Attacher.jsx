define([
    'react',
    'create-react-class',
    'prop-types',
    'util/component/attacher'
], function(React, createReactClass, PropTypes, attacher) {
    'use strict';

    const Attacher = createReactClass({

        propTypes: {
            componentPath: PropTypes.string,
            component: PropTypes.func,
            behavior: PropTypes.object,
            legacyMapping: PropTypes.object,
            nodeType: PropTypes.string,
            nodeStyle: PropTypes.object,
            nodeClassName: PropTypes.string
        },

        getDefaultProps() {
            return { nodeType: 'div', nodeStyle: {}, nodeClassName: '' };
        },

        getInitialState() {
            return { element: null }
        },

        componentDidMount() {
            this.reattach(this.props);
        },

        componentWillReceiveProps(nextProps) {
            if (nextProps !== this.props) {
                this.reattach(nextProps);
            }
        },

        componentWillUnmount() {
            if (this.attacher) {
                this.attacher.teardown();
                this.attacher = null;
            }
        },

        render() {
            const { nodeType, nodeStyle, nodeClassName } = this.props;
            const { element } = this.state;

            return element ? element : React.createElement(nodeType, {
                ref: 'node',
                style: nodeStyle,
                className: nodeClassName
            });
        },

        reattach(props) {
            const { component, componentPath, legacyMapping, behavior, nodeType, nodeStyle, nodeClassName, ...rest } = props;

            if (!component && !componentPath) {
                throw new Error('Attacher requires either component or componentPath')
            }

            const inst = (this.attacher || (this.attacher = attacher({ preferDirectReactChildren: true })))
                .path(componentPath)
                .component(component)
                .params(rest);

            if (this.refs.node) {
                inst.node(this.refs.node)
            }

            if (behavior) {
                inst.behavior(behavior)
            }

            if (legacyMapping) {
                inst.legacyMapping(legacyMapping)
            }

            inst.attach({
                teardown: true,
                teardownOptions: { react: false },
                emptyFlight: true
            }).then(attach => {
                if (this.attacher) {
                    if (attach._reactElement) {
                        this.setState({ element: attach._reactElement })
                    }
                    this.afterAttach();
                }
            })
        },

        afterAttach() {
            const afterAttach = this.props.afterAttach;

            if (_.isFunction(afterAttach)) {
                afterAttach(this.attacher);
            }
        }
    });

    return Attacher;
});
