package org.visallo.web.structuredingest.parquet;

import org.visallo.webster.Handler;
import org.visallo.core.model.Description;
import org.visallo.core.model.Name;
import org.visallo.web.WebApp;
import org.visallo.web.WebAppPlugin;

import javax.servlet.ServletContext;

@Name("Structured File Parquet support")
@Description("Adds support for importing structured data from Parquet files")
public class ParquetWebAppPlugin implements WebAppPlugin {
    @Override
    public void init(WebApp app, ServletContext servletContext, Handler authenticationHandler) {

        app.registerJavaScript("/org/visallo/web/structuredingest/parquet/js/plugin.js");
        app.registerJavaScript("/org/visallo/web/structuredingest/parquet/js/textSection.js", false);
    }
}
