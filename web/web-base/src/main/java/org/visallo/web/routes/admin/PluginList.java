package org.visallo.web.routes.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.json.JSONArray;
import org.json.JSONObject;
import org.visallo.core.bootstrap.lib.LibLoader;
import org.visallo.core.config.Configuration;
import org.visallo.core.ingest.FileImportSupportingFileHandler;
import org.visallo.core.ingest.graphProperty.GraphPropertyWorker;
import org.visallo.core.ingest.graphProperty.PostMimeTypeWorker;
import org.visallo.core.ingest.graphProperty.TermMentionFilter;
import org.visallo.core.model.Description;
import org.visallo.core.model.Name;
import org.visallo.core.model.user.UserListener;
import org.visallo.core.util.ServiceLoaderUtil;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.WebAppPlugin;

import java.io.File;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

@Singleton
public class PluginList implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(PluginList.class);
    private final Configuration configuration;

    @Inject
    public PluginList(Configuration configuration) {
        this.configuration = configuration;
    }

    @Handle
    public JSONObject handle() throws Exception {
        JSONObject json = new JSONObject();

        json.put("loadedLibFiles", getLoadedLibFilesJson());
        json.put("graphPropertyWorkers", getGraphPropertyWorkersJson());
        json.put("postMimeTypeWorkers", getPostMimeTypeWorkersJson());
        json.put("userListeners", getUserListenersJson());
        json.put("libLoaders", getLibLoadersJson());
        json.put("fileImportSupportingFileHandlers", getFileImportSupportingFileHandlersJson());
        json.put("termMentionFilters", getTermMentionFiltersJson());
        json.put("webAppPlugins", getWebAppPluginsJson());

        return json;
    }

    private JSONArray getUserListenersJson() {
        JSONArray json = new JSONArray();
        for (Class<? extends UserListener> userListenerClass : ServiceLoaderUtil.loadClasses(UserListener.class, configuration)) {
            json.put(getUserListenerJson(userListenerClass));
        }
        return json;
    }

    private JSONObject getUserListenerJson(Class<? extends UserListener> userListenerClass) {
        JSONObject json = new JSONObject();
        getGeneralInfo(json, userListenerClass);
        return json;
    }

    private JSONArray getGraphPropertyWorkersJson() {
        JSONArray json = new JSONArray();
        for (Class<? extends GraphPropertyWorker> graphPropertyWorkerClass : ServiceLoaderUtil.loadClasses(GraphPropertyWorker.class, configuration)) {
            json.put(getGraphPropertyWorkerJson(graphPropertyWorkerClass));
        }
        return json;
    }

    private JSONObject getGraphPropertyWorkerJson(Class<? extends GraphPropertyWorker> graphPropertyWorkerClass) {
        JSONObject json = new JSONObject();
        getGeneralInfo(json, graphPropertyWorkerClass);
        return json;
    }

    private JSONArray getPostMimeTypeWorkersJson() {
        JSONArray json = new JSONArray();
        for (Class<? extends PostMimeTypeWorker> postMimeTypeWorkerClass : ServiceLoaderUtil.loadClasses(PostMimeTypeWorker.class, configuration)) {
            json.put(getPostMimeTypeWorkerJson(postMimeTypeWorkerClass));
        }
        return json;
    }

    private JSONObject getPostMimeTypeWorkerJson(Class<? extends PostMimeTypeWorker> postMimeTypeWorkerClass) {
        JSONObject json = new JSONObject();
        getGeneralInfo(json, postMimeTypeWorkerClass);
        return json;
    }

    private JSONArray getLoadedLibFilesJson() {
        JSONArray json = new JSONArray();
        for (File loadedLibFile : LibLoader.getLoadedLibFiles()) {
            json.put(getLoadedLibFileJson(loadedLibFile));
        }
        return json;
    }

    private JSONObject getLoadedLibFileJson(File loadedLibFile) {
        JSONObject json = new JSONObject();
        json.put("fileName", loadedLibFile.getAbsolutePath());
        return json;
    }

    private JSONArray getLibLoadersJson() {
        JSONArray json = new JSONArray();
        for (Class<? extends LibLoader> libLoaderClass : ServiceLoaderUtil.loadClasses(LibLoader.class, configuration)) {
            json.put(getLibLoaderJson(libLoaderClass));
        }
        return json;
    }

    private JSONObject getLibLoaderJson(Class<? extends LibLoader> libLoaderClass) {
        JSONObject json = new JSONObject();
        getGeneralInfo(json, libLoaderClass);
        return json;
    }

    private JSONArray getFileImportSupportingFileHandlersJson() {
        JSONArray json = new JSONArray();
        for (Class<? extends FileImportSupportingFileHandler> fileImportSupportingFileHandlerClass : ServiceLoaderUtil.loadClasses(FileImportSupportingFileHandler.class, configuration)) {
            json.put(getFileImportSupportingFileHandlerJson(fileImportSupportingFileHandlerClass));
        }
        return json;
    }

    private JSONObject getFileImportSupportingFileHandlerJson(Class<? extends FileImportSupportingFileHandler> fileImportSupportingFileHandlerClass) {
        JSONObject json = new JSONObject();
        getGeneralInfo(json, fileImportSupportingFileHandlerClass);
        return json;
    }

    private JSONArray getTermMentionFiltersJson() {
        JSONArray json = new JSONArray();
        for (Class<? extends TermMentionFilter> termMentionFilterClass : ServiceLoaderUtil.loadClasses(TermMentionFilter.class, configuration)) {
            json.put(getTermMentionFilterJson(termMentionFilterClass));
        }
        return json;
    }

    private JSONObject getTermMentionFilterJson(Class<? extends TermMentionFilter> termMentionFilterClass) {
        JSONObject json = new JSONObject();
        getGeneralInfo(json, termMentionFilterClass);
        return json;
    }

    private JSONArray getWebAppPluginsJson() {
        JSONArray json = new JSONArray();
        for (Class<? extends WebAppPlugin> webAppPluginClass : ServiceLoaderUtil.loadClasses(WebAppPlugin.class, configuration)) {
            json.put(getWebAppPluginJson(webAppPluginClass));
        }
        return json;
    }

    private JSONObject getWebAppPluginJson(Class<? extends WebAppPlugin> webAppPluginClass) {
        JSONObject json = new JSONObject();
        getGeneralInfo(json, webAppPluginClass);
        return json;
    }

    private static void getGeneralInfo(JSONObject json, Class clazz) {
        json.put("className", clazz.getName());

        Name nameAnnotation = (Name) clazz.getAnnotation(Name.class);
        if (nameAnnotation != null) {
            json.put("name", nameAnnotation.value());
        }

        Description descriptionAnnotation = (Description) clazz.getAnnotation(Description.class);
        if (descriptionAnnotation != null) {
            json.put("description", descriptionAnnotation.value());
        }

        Manifest manifest = getManifest(clazz);
        if (manifest != null) {
            Attributes mainAttributes = manifest.getMainAttributes();
            json.put("projectVersion", mainAttributes.getValue("Project-Version"));
            json.put("gitRevision", mainAttributes.getValue("Git-Revision"));
            json.put("builtBy", mainAttributes.getValue("Built-By"));
            String value = mainAttributes.getValue("Built-On-Unix");
            if (value != null) {
                json.put("builtOn", Long.parseLong(value));
            }
        }
    }

    private static Manifest getManifest(Class clazz) {
        try {
            String className = clazz.getSimpleName() + ".class";
            URL resource = clazz.getResource(className);
            if (resource == null) {
                LOGGER.error("Could not get class manifest: " + clazz.getName() + ", could not find resource: " + className);
                return null;
            }
            String classPath = resource.toString();
            if (!classPath.startsWith("jar")) {
                return null; // Class not from JAR
            }
            String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF";
            return new Manifest(new URL(manifestPath).openStream());
        } catch (Exception ex) {
            LOGGER.error("Could not get class manifest: " + clazz.getName(), ex);
            return null;
        }
    }
}
