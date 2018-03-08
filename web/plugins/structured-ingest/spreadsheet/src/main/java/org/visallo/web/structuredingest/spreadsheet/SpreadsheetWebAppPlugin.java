package org.visallo.web.structuredingest.spreadsheet;

import org.visallo.webster.Handler;
import org.visallo.core.model.Description;
import org.visallo.core.model.Name;
import org.visallo.web.WebApp;
import org.visallo.web.WebAppPlugin;

import javax.servlet.ServletContext;

@Name("Structured File CSV and Excel support")
@Description("Adds support for importing structured data from CSV and Excel files")
public class SpreadsheetWebAppPlugin implements WebAppPlugin {
    @Override
    public void init(WebApp app, ServletContext servletContext, Handler authenticationHandler) {

        app.registerJavaScript("/org/visallo/web/structuredingest/spreadsheet/plugin.js");

    }
}
