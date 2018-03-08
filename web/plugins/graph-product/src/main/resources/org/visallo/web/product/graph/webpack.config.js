var path = require('path');
var webpack = require('webpack');
var VisalloAmdExternals = [
    'components/DroppableHOC',
    'product/toolbar/ProductToolbar',
    'components/RegistryInjectorHOC',
    'components/Attacher',
    'configuration/plugins/registry',
    'data/web-worker/store/actions',
    'data/web-worker/store/product/actions-impl',
    'data/web-worker/store/product/actions',
    'data/web-worker/store/user/actions-impl',
    'data/web-worker/store/user/actions',
    'data/web-worker/store/user/selectors',
    'data/web-worker/store/product/selectors',
    'data/web-worker/store/selection/actions',
    'data/web-worker/store/user/actions-impl',
    'data/web-worker/store/element/actions-impl',
    'data/web-worker/store/element/selectors',
    'data/web-worker/store/selection/actions-impl',
    'data/web-worker/store/undo/actions-impl',
    'data/web-worker/store/undo/actions',
    'data/web-worker/store/workspace/actions-impl',
    'data/web-worker/store/workspace/actions',
    'data/web-worker/store/ontology/selectors',
    'data/web-worker/util/ajax',
    'org/visallo/web/product/graph/dist/actions-impl',
    'public/v1/api',
    'util/component/attacher',
    'util/formatters',
    'util/vertex/formatters',
    'util/retina',
    'util/dnd',
    'util/deepObjectCache',
    'util/parsers',
    'util/withContextMenu',
    'util/withDataRequest',
    'util/withTeardown',
    'util/withFormFieldErrors',
    'util/ontology/relationshipSelect',
    'detail/dropdowns/propertyForm/justification',
    'util/visibility/edit',
    'flight/lib/component',
    'fast-json-patch',
    'updeep',
    'underscore',
    'colorjs',
    'react',
    'create-react-class',
    'prop-types',
    'react-dom',
    'redux',
    'react-redux'
].map(path => ({ [path]: { amd: path, commonjs2: false, commonjs: false }}));

var baseConfig = {
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: '[name].js',
    library: '[name]',
    libraryTarget: 'umd',
  },
  externals: VisalloAmdExternals,
  resolve: {
    extensions: ['.js', '.jsx', '.hbs', '.ejs'],
    alias: {
        cytoscape: path.resolve(__dirname, 'node_modules/@visallo/cytoscape')
    }
  },
  module: {
    rules: [
        {
            test: /\.ejs$/,
            exclude: /(dist|node_modules)/,
            use: [
                { loader: 'ejs-compiled-loader' }
            ]
        },
        {
            test: /\.jsx?$/,
            exclude: /(dist|node_modules)/,
            use: [
                { loader: 'babel-loader' }
            ]
        },
        {
            test: /\.jsx?$/,
            exclude: /(node_modules|__mocks__|__tests__|dist)/,
            use: [
                { loader: 'eslint-loader' }
            ]
        }
    ]
  },
  devtool: 'source-map',
  plugins: [
    new webpack.optimize.UglifyJsPlugin({
        mangle: process.env.NODE_ENV !== 'development',
        sourceMap: true,
        compress: {
            drop_debugger: false,
            warnings: true
        }
    })
  ]
}

module.exports = [
    Object.assign({}, baseConfig, {
        entry: {
            'store-changes': './worker/store-changes.js',
            'actions-impl': './worker/actions-impl.js',
            'plugin-worker': './worker/plugin.js'
        },
        target: 'webworker'
    }),
    Object.assign({}, baseConfig, {
        entry: {
            Graph: './GraphContainer.jsx',
            EdgeLabel: './options/EdgeLabel.jsx',
            SnapToGrid: './options/SnapToGrid.jsx',
            FindPathPopoverContainer: './popovers/findPath/FindPathPopoverContainer.jsx',
            CollapsedNodePopoverConfig: './popovers/collapsedNode/CollapsedNodePopoverConfig.jsx'
        },
        target: 'web'
    })
];
