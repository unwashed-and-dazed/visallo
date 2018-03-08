package org.visallo.web.product.map;

import org.visallo.webster.Handler;
import org.visallo.core.model.Description;
import org.visallo.core.model.Name;
import org.visallo.web.VisalloCsrfHandler;
import org.visallo.web.WebApp;
import org.visallo.web.WebAppPlugin;
import org.visallo.web.product.map.routes.RemoveVertices;
import org.visallo.web.product.map.routes.UpdateVertices;

import javax.servlet.ServletContext;

@Name("Product: Map")
@Description("Map visualization for entities containing geolocation data")
public class MapWebAppPlugin implements WebAppPlugin {
    @Override
    public void init(WebApp app, ServletContext servletContext, Handler authenticationHandler) {
        Class<? extends Handler> authenticationHandlerClass = authenticationHandler.getClass();
        Class<? extends Handler> csrfHandlerClass = VisalloCsrfHandler.class;

        app.post("/product/map/vertices/remove", authenticationHandlerClass, csrfHandlerClass, RemoveVertices.class);
        app.post("/product/map/vertices/update", authenticationHandlerClass, csrfHandlerClass, UpdateVertices.class);

        app.registerJavaScript("/org/visallo/web/product/map/plugin.js");
        app.registerJavaScript("/org/visallo/web/product/map/detail/pluginGeoShapeDetail.js", true);

        app.registerCompiledJavaScript("/org/visallo/web/product/map/dist/geoShapePreview.js");
        app.registerCompiledJavaScript("/org/visallo/web/product/map/dist/MapLayersContainer.js");
        app.registerCompiledJavaScript("/org/visallo/web/product/map/dist/Map.js");
        app.registerCompiledJavaScript("/org/visallo/web/product/map/dist/actions-impl.js");

        app.registerCompiledWebWorkerJavaScript("/org/visallo/web/product/map/dist/plugin-worker.js");

        app.registerResourceBundle("/org/visallo/web/product/map/messages.properties");

        app.registerLess("/org/visallo/web/product/map/layers/mapLayers.less");
        app.registerLess("/org/visallo/web/product/map/detail/geoShapeDetail.less");
    }
}
