var path = require('path');
var webpack = require('webpack');
var VisalloAmdExternals = [
    'components/Attacher',
    'components/RegistryInjectorHOC',
    'configuration/plugins/registry',
    'data/web-worker/store/actions',
    'data/web-worker/util/ajax',
    'updeep',
    'util/promise',
    'util/formatters',
    'react',
    'create-react-class',
    'prop-types',
    'react-dom',
    'redux',
    'react-redux'
].map(path => ({ [path]: { amd: path, commonjs2: false, commonjs: false }}));

module.exports = {
  entry: {
    Config: './js/S3Container.jsx',
    BasicAuth: './js/auth/BasicAuth.jsx',
    SessionAuth: './js/auth/SessionAuth.jsx',
    'actions-impl': './js/worker/actions-impl.js',
    'plugin-worker': './js/worker/plugin.js'
  },
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: '[name].js',
    library: '[name]',
    libraryTarget: 'umd',
  },
  externals: VisalloAmdExternals,
  resolve: {
    extensions: ['.js', '.jsx']
  },
  module: {
    rules: [
        {
            test: /\.jsx?$/,
            exclude: /(node_modules)/,
            use: [
                { loader: 'babel-loader' }
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
};
