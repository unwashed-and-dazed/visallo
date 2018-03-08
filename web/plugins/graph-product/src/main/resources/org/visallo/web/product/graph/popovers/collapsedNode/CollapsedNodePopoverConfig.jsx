define([
    'create-react-class',
    'prop-types',
    'react-redux',
    'data/web-worker/store/product/selectors'
], function (createReactClass, PropTypes, redux, productSelectors) {
    'use strict';

    const CollapsedNodePopoverConfig = createReactClass({
        propTypes: {
            collapsedNode: PropTypes.object,
            collapsedNodeId: PropTypes.string.isRequired,
            close: PropTypes.func.isRequired,
            onRename: PropTypes.func.isRequired
        },

        getInitialState() {
            return {}
        },

        componentDidMount() {
            $(document).on('keydown.org-visallo-graph-collapsed-node-popover', (event) => {
                switch (event.which) {
                    case 13: this.onSubmit(); break; //enter
                    case 27: this.props.close(); break; //esc
                }
            });
        },

        componentWillUnmount() {
            $(document).off('.org-visallo-graph-collapsed-node-popover');
        },

        componentWillReceiveProps(nextProps) {
            if (!nextProps.collapsedNode) {
                this.props.close();
            }
        },

        render() {
            const { title } = this.state;
            const { title: initialTitle } = this.props.collapsedNode;
            const hasChanges = title !== undefined && title !== initialTitle;

            return (
                <div className="title-edit">
                    <input
                        className="rename"
                        style={{ flexGrow: 1, flexShrink: 1, margin: 0 }}
                        type="text"
                        placeholder={i18n('org.visallo.web.product.graph.collapsedNode.popover.automatic.title')}
                        value={title !== undefined ? title : initialTitle ? initialTitle : ''}
                        onChange={this.onTitleChange}
                    />
                    <button
                        className="btn btn-primary"
                        style={{ flexGrow: 0, flexShrink: 1, flexBasis: '20%', margin: 0, marginLeft: '0.25em'}}
                        disabled={!hasChanges}
                        onClick={this.onSubmit}>
                        {i18n('org.visallo.web.product.graph.collapsedNode.popover.rename')}
                    </button>
                </div>
            );
        },

        onTitleChange(event) {
            this.setState({ title: event.target.value });
        },

        onSubmit() {
            this.props.onRename(this.state.title);
            this.props.close();
        }
    });

    return redux.connect(
        (state, props) => {
            const product = productSelectors.getProduct(state);
            const collapsedNode = product.extendedData
                && product.extendedData.compoundNodes
                && product.extendedData.compoundNodes[props.collapsedNodeId];
            return {
                ...props,
                collapsedNode
            }
        }
    )(CollapsedNodePopoverConfig);
});
