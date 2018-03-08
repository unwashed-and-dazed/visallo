package org.visallo.web.routes;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ServletContextTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.web.ContentSecurityPolicy;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.apache.commons.io.IOUtils;
import org.visallo.core.config.Configuration;
import org.visallo.web.WebApp;
import org.visallo.web.WebConfiguration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class Index implements ParameterizedHandler {
    private static final String HEADER_CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String PLUGIN_JS_RESOURCES_BEFORE_AUTH_PARAM = "pluginJsResourcesBeforeAuth";
    private static final String PLUGIN_JS_RESOURCES_WEB_WORKER_PARAM = "pluginJsResourcesWebWorker";
    private static final String PLUGIN_JS_RESOURCES_AFTER_AUTH_PARAM = "pluginJsResourcesAfterAuth";
    private static final String PLUGIN_CSS_RESOURCES_PARAM = "pluginCssResources";
    private static final String LOGO_IMAGE_DATA_URI = "logoDataUri";
    private static final String SHOW_VERSION_COMMENTS = "showVersionComments";
    private static final String DEV_MODE = "devMode";
    private static final String LOGO_PATH_BUNDLE_KEY = "visallo.loading-logo.path";
    private static final String CONTEXT_PATH = "contextPath";
    private static final Map<String, String> MESSAGE_BUNDLE_PARAMS = ImmutableMap.of(
            "title", "visallo.title",
            "description", "visallo.description"
    );

    private final ContentSecurityPolicy contentSecurityPolicy;

    private String indexHtml;
    private boolean showVersionComments;

    @Inject
    public Index(Configuration configuration, ContentSecurityPolicy contentSecurityPolicy) {
        showVersionComments = configuration.getBoolean(WebConfiguration.SHOW_VERSION_COMMENTS, true);
        this.contentSecurityPolicy = contentSecurityPolicy;
    }

    @Handle
    public void handle(
            WebApp webApp,
            ResourceBundle resourceBundle,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws Exception {
        response.setContentType("text/html");
        response.setCharacterEncoding(Charsets.UTF_8.name());
        if (!response.containsHeader(HEADER_CONTENT_SECURITY_POLICY)) {
            response.addHeader(HEADER_CONTENT_SECURITY_POLICY, contentSecurityPolicy.generatePolicy(request));
        }

        response.getWriter().write(getIndexHtml(request, webApp, resourceBundle));
    }

    private String getIndexHtml(HttpServletRequest request, WebApp app, ResourceBundle resourceBundle) throws IOException {
        boolean devMode = app.isDevModeEnabled();
        if (indexHtml == null || devMode) {
            Map<String, Object> context = new HashMap<>();
            context.put(CONTEXT_PATH, request.getContextPath());
            context.put(PLUGIN_JS_RESOURCES_BEFORE_AUTH_PARAM, app.getPluginsJsResourcesBeforeAuth());
            context.put(PLUGIN_JS_RESOURCES_WEB_WORKER_PARAM, app.getPluginsJsResourcesWebWorker());
            context.put(PLUGIN_JS_RESOURCES_AFTER_AUTH_PARAM, app.getPluginsJsResourcesAfterAuth());
            context.put(PLUGIN_CSS_RESOURCES_PARAM, app.getPluginsCssResources());
            context.put(LOGO_IMAGE_DATA_URI, getLogoImageDataUri(request, resourceBundle));
            context.put(SHOW_VERSION_COMMENTS, showVersionComments);
            context.put(DEV_MODE, devMode);
            for (Map.Entry<String, String> param : MESSAGE_BUNDLE_PARAMS.entrySet()) {
                context.put(param.getKey(), resourceBundle.getString(param.getValue()));
            }
            TemplateLoader templateLoader = new ServletContextTemplateLoader(request.getServletContext(), "/", ".hbs");
            Handlebars handlebars = new Handlebars(templateLoader);
            Template template = handlebars.compile("index");
            indexHtml = template.apply(context);
        }
        return indexHtml;
    }

    private String getLogoImageDataUri(HttpServletRequest request, ResourceBundle resourceBundle) throws IOException {
        String logoPathBundleKey = resourceBundle.getString(LOGO_PATH_BUNDLE_KEY);
        checkNotNull(logoPathBundleKey, LOGO_PATH_BUNDLE_KEY + " configuration not found");
        try (InputStream in = getResourceAsStream(request, logoPathBundleKey)) {
            checkNotNull(in, "Could not find resource: " + logoPathBundleKey);
            byte[] bytes = IOUtils.toByteArray(in);
            return "data:image/png;base64," + DatatypeConverter.printBase64Binary(bytes);
        }
    }

    private InputStream getResourceAsStream(HttpServletRequest request, String path) {
        InputStream in = request.getServletContext().getResourceAsStream(path);
        if (in == null) {
            in = getClass().getResourceAsStream(path);
        }
        return in;
    }
}
