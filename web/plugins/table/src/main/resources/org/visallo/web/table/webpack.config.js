var path = require('path');
var webpack = require('webpack');
var VisalloAmdExternals = [
 'classnames',
 'public/v1/api',
 'util/popovers/withPopover',
 'org/visallo/web/table/hbs/columnConfigPopover',
 'react',
 'react-virtualized',
 'create-react-class',
 'prop-types',
 'react-dom',
 'redux',
 'react-redux',
 'data/web-worker/store/selection/actions',
 'data/web-worker/store/product/selectors',
 'data/web-worker/store/ontology/selectors'
].map(path => ({ [path]: { amd: path, commonjs2: false, commonjs: false }}));

module.exports = {
  entry: {
    card: './js/card/SavedSearchTableContainer.jsx'
  },
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: '[name].js',
    library: '[name]',
    libraryTarget: 'umd',
  },
  externals: VisalloAmdExternals,
  resolve: {
    extensions: ['.js', '.jsx', '.hbs']
  },
  module: {
    rules: [
        {
            test: /\.jsx?$/,
            include: path.join(__dirname, 'js'),
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
