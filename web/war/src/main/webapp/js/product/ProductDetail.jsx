define([
    'create-react-class',
    'prop-types',
    './ProductDetailLoading',
    'components/ErrorBoundary'
], function(createReactClass, PropTypes, ProductDetailEmpty, ErrorBoundary) {
    'use strict';

    const ProductDetail = createReactClass({
        propTypes: {
            product: PropTypes.shape({
                id: PropTypes.string.isRequired
            }).isRequired,
            extension: PropTypes.shape({
                componentPath: PropTypes.string.isRequired
            }).isRequired,
            workspace: PropTypes.object
        },

        getInitialState: () => ({ Component: null }),

        componentDidMount() {
            this.requestComponent(this.props, !this.props.product.extendedData);
        },

        componentWillReceiveProps(nextProps) {
            this.requestComponent(nextProps, nextProps.product !== this.props.product);
        },

        render() {
            const { Component } = this.state;
            const { product, editable, hasPreview, workspace } = this.props;
            const { extendedData, title } = product;

            return (
                Component && extendedData && workspace ? (
                    <ErrorBoundary>
                        <Component product={this.props.product} hasPreview={hasPreview}></Component>
                    </ErrorBoundary>
                ) : (
                    <ProductDetailEmpty
                        editable={editable}
                        type={i18n(this.props.extension.identifier + '.name')}
                        title={title}
                        padding={this.props.padding}
                    />
                )
            )
        },

        requestComponent(props, requestProduct) {
            if (requestProduct) {
                props.onGetProduct(props.product.id);
            }

            if (props.extension.componentPath !== this.props.extension.componentPath || !this.state.Component) {
                this.setState({ Component: null })
                Promise.require(props.extension.componentPath).then((C) => this.setState({ Component: C }))
            }
        }
    });

    return ProductDetail;
});
