# Getting Started

While we recommend reading all the pages in this Developer Guide, the steps below are the fastest path to getting an instance of Visallo up and running in no time. Please make sure you have all [required dependencies](getting-started/dependencies.md) installed before attempting any of the steps below.

First, clone the source code. On Mac or Linux use

      git clone git://github.com/visallo/visallo.git
      
On Windows, clone using the configuration: `core.symlinks`. Visallo uses symlinks and will fail in strange ways if Git for Windows is not setup correctly (This clone will fail if symlinks aren't compiled/enabled or the user doesn't have privileges.) View the [Windows Git documentation](https://github.com/git-for-windows/git/wiki/Symbolic-Links) for more information.

      git clone -c core.symlinks=true git://github.com/visallo/visallo.git

Second, change directories to the checked out code. This is your `$PROJECT_DIR` directory.

      cd visallo

Third, compile the application (optionally run tests.)
      
      mvn -DskipTests compile      

Fourth, run the web application.

      mvn -am -pl dev/tomcat-server -P dev-tomcat-run compile

Once the log output stops, your server will be available at [http://localhost:8888](http://localhost:8888).

