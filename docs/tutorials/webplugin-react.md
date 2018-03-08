# Making a Web Plugin – React

## Introduction

Writing components in [React](https://facebook.github.io/react) is now the preferred way to extend Visallo with custom interfaces. Most extension points already support React, but check the [documentation](../extension-points/front-end/index.md) to make sure.

When writing a web app plugin in Visallo there are two methods to include React JavaScript components:

1. Use `registerJavaScriptComponent` to include a React `jsx` component from the plugins resource folder.

2. Integrate a build step to your plugins `pom.xml` to transpile `jsx` components and then register them with `registerCompiledJavaScript`.

## 1. `registerJavaScriptComponent`

**PROS**
* Easy to get started, or for components with minimal complexity. Doesn't require separate build step.

**CONS**:
* Doesn't scale as well with many files. Each file must be registered.
* Each file registered slows server startup as they are compiled at runtime.
* Compilation failures will happen at runtime.

### Example

This example will create a plugin that [adds a new dashboard card](../extension-points/front-end/dashboard/item.md) that users can add to their dashboard.

Create a java file at ```plugins/web/src/main/java/com/visalloexample/helloworld/web/ReactDemoWebAppPlugin.java``` and put the following into that file:

```java
package com.visalloexample.helloworld.web;

import org.visallo.core.model.Description;
import org.visallo.core.model.Name;
import org.visallo.web.WebApp;
import org.visallo.web.WebAppPlugin;
import org.visallo.webster.Handler;

import javax.servlet.ServletContext;

@Name("React Web Demo")
@Description("Register a React JSX File")
public class ReactDemoWebAppPlugin implements WebAppPlugin {
    public void init(WebApp app, ServletContext servletContext, Handler authenticationHandler) {

      // Register plugin to use extension registry
      app.registerJavaScript("/com/visalloexample/helloworld/web/react-plugin.js");

      // Register React components
      app.registerJavaScriptComponent("/com/visalloexample/helloworld/web/ReactDemo.jsx");
      app.registerJavaScriptComponent("/com/visalloexample/helloworld/web/ReactDemoConfig.jsx");
    }
}
```

Next, we need to load our web plugin into the web server using service loading, so open up the ```plugins/web/src/main/resources/META-INF/services/org.visallo.web.WebAppPlugin``` and add the line

```
com.visalloexample.helloworld.web.ReactDemoWebAppPlugin
```

so now that file is going to look like:

```properties
com.visalloexample.helloworld.web.ExampleWebAppPlugin
com.visalloexample.helloworld.web.SelectedVertexWebAppPlugin
com.visalloexample.helloworld.web.ReactDemoWebAppPlugin
```


`ReactDemo.jsx` will compile to `ReactDemo.js`, and creates a SourceMap at `ReactDemo.src.js`.

```js
// plugins/web/src/main/resources/com/visalloexample/helloworld/web/react-plugin.js
define(['public/v1/api'], function(api) {
    api.registry.registerExtension('org.visallo.web.dashboard.item', {
        title: 'React Demo',
        description: 'React dashboard card demo',
        identifier: 'com-visalloexample-helloworld-web-react',

        // Note: Leave off the file extension as requirejs assumes ".js" which
        // is created at runtime.
        componentPath: 'com/visalloexample/helloworld/web/ReactDemo',
        configurationPath: 'com/visalloexample/helloworld/web/ReactDemoConfig'
    })
})
```


```js
// plugins/web/src/main/resources/com/visalloexample/helloworld/web/ReactDemo.jsx
// Visallo registers 'react', 'create-react-class', and 'prop-types' in RequireJS.
define(['create-react-class'], function(createReactClass) {
    const ReactDemo = createReactClass({
        render() {
            const { item } = this.props;
            const { configuration } = item;
            const { val = 'Not Set' } = configuration
            return (<div>
                <h1>Hello Dashboard Card with React</h1>
                <h2>Config = {val}</h2>
            </div>);
        }
    })

    return ReactDemo;
})
```

```js
// plugins/web/src/main/resources/com/visalloexample/helloworld/web/ReactDemoConfig.jsx
define(['create-react-class', 'prop-types'], function(createReactClass, PropTypes) {
    const ReactDemoConfig = createReactClass({
        propTypes: {
            item: PropTypes.shape({
                configuration: PropTypes.object.isRequired
            }).isRequired,
            extension: PropTypes.object.isRequired
        },
        render() {
            const { item } = this.props;
            const { configuration } = item;
            const { val = 'Not Set' } = configuration
            return (<button onClick={this.onClick}>Config = {val}</button>);
        },
        onClick() {
            const { item, extension, configurationChanged } = this.props;
            const val = item.configuration.val || 0;
            const newConfig = {
                ...item.configuration,
                val: val + 1
            };
            configurationChanged({ item: { ...item, configuration: newConfig }, extension: extension });
        }
    })

    return ReactDemoConfig;
})
```


All JSX components are compiled using babel so ES6/ES2015 syntax works in `.jsx` files registered with `registerJavaScriptComponent`.

## 2. `registerCompiledJavaScript`

Recommended for complex interface plugins that have deeper component hierarchy.

**PROS**
* Build step run once at build time that combines all dependencies, so no server startup delay.
* Allows use of custom transpile / babel settings.
* Performance of plugin at runtime is better as its only one request for all dependencies.
* Easier to include other build steps like linting, testing, etc.
* Compilation failures will happen at build time.

**CONS**
* Adds complexity to build, must configure maven to run webpack, define webpack build settings.

### Example

This example will create a plugin that [adds a new dashboard card](../extension-points/front-end/dashboard/item.md) that users can add to their dashboard using webpack to build.

All these files remain the same as previous example: `ReactDemo.jsx`, and `ReactDemoConfig.jsx`, but now we change `pom.xml`, `react-plugin.js`, and `ReactDemoWebAppPlugin.java`.


First, lets create a `package.json` to manage our plugins dependencies in our `plugins/web/src/main/resources/com/visalloexample/helloworld/web` directory.

```
//package.json
{
  "name": "web",
  "version": "1.0.0",
  "main": "js/react-plugin.js",
  "devDependencies": {
    "babel-core": "^6.26.0",
    "babel-plugin-transform-object-rest-spread": "^6.26.0",
    "babel-plugin-transform-react-display-name": "^6.25.0",
    "babel-plugin-transform-react-jsx": "^6.24.1",
    "babel-preset-es2015": "^6.24.1",
    "react": "^16.2.0",
    "webpack": "^3.10.0"
  },
  "dependencies": {
    "babel-loader": "^7.1.2"
  }
}
```

Run `yarn install` (`npm install -g yarn` if you don't have `yarn` installed) in the `plugins/web/src/main/resources/com/visalloexample/helloworld/web` directory.

Now, configure babel using `.babelrc`. Make sure to be in the `plugins/web/src/main/resources/com/visalloexample/helloworld/web/` directory before running the command below.

```sh
curl -O https://raw.githubusercontent.com/visallo/visallo/master/web/plugins/map-product/src/main/resources/org/visallo/web/product/map/.babelrc > .babelrc
```

Create a webpack configuration file: `plugins/web/src/main/resources/com/visalloexample/helloworld/web/webpack.config.js`

```js
// webpack.config.js
var path = require('path');
var webpack = require('webpack');
var VisalloAmdExternals = [
    'public/v1/api',
    'create-react-class'
].map(path => ({ [path]: { amd: path, commonjs2: false, commonjs: false }}));

module.exports = {
  entry: {
    ReactDemo: './ReactDemo.jsx',
    ReactDemoConfig: './ReactDemoConfig.jsx'
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
        compress: {
            drop_debugger: false,
            warnings: true
        }
    })
  ]
};
```

Try a build by running webpack from the `plugins/web/src/main/resources/com/visalloexample/helloworld/web` directory.

```sh
node ./node_modules/webpack/bin/webpack.js
```

Now, lets change the plugin to register the compiled files.

```java
// ReactDemoWebAppPlugin.java
@Name("React Web Demo")
@Description("Register a React JSX File")
public class ReactDemoWebAppPlugin implements WebAppPlugin {
    public void init(WebApp app, ServletContext servletContext, Handler authenticationHandler) {

        // Register plugin to use extension registry
        // We don't use webpack for this file
        app.registerJavaScript("/com/visalloexample/helloworld/web/react-plugin.js");

        // Register React components by pointing to the webpack compiled versions in dist folder
        app.registerCompiledJavaScript("/com/visalloexample/helloworld/web/dist/ReactDemo.js");
        app.registerCompiledJavaScript("/com/visalloexample/helloworld/web/dist/ReactDemoConfig.js");
    }
}
```

Change the plugin to use the compiled files.

```java
// react-plugin.js
define(['public/v1/api'], function(api) {
    api.registry.registerExtension('org.visallo.web.dashboard.item', {
        title: 'React Demo',
        description: 'React dashboard card demo',
        identifier: 'com-visalloexample-helloworld-web-react',

        // Note: Leave off the file extension as requirejs assumes ".js" which
        // is created at runtime.
        componentPath: 'com/visalloexample/helloworld/web/dist/ReactDemo',
        configurationPath: 'com/visalloexample/helloworld/web/dist/ReactDemoConfig'
    })
})
```

Finally, we need to integrate yarn and webpack into the maven build. In your plugin's `pom.xml`, add the following.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.github.eirslett</groupId>
            <artifactId>frontend-maven-plugin</artifactId>
            <version>${plugin.frontend}</version>
            <configuration>
                <workingDirectory>src/main/resources/com/visalloexample/helloworld/web</workingDirectory>
                <installDirectory>${frontend.installDirectory}</installDirectory>
            </configuration>
            <executions>
               <execution>
                   <id>yarn install</id>
                   <goals>
                       <goal>yarn</goal>
                   </goals>
                   <configuration>
                       <arguments>install --production=false</arguments>
                   </configuration>
               </execution>
               <execution>
                   <id>webpack build</id>
                   <goals>
                       <goal>webpack</goal>
                   </goals>
                   <phase>generate-resources</phase>
               </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Now try to run `mvn compile`, there should be yarn and webpack commands running in the log.
