package org.visallo.web;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.visallo.web.routes.security.ContentSecurityPolicyReport;
import org.visallo.webster.Handler;
import org.visallo.webster.handlers.StaticResourceHandler;
import org.visallo.core.bootstrap.InjectHelper;
import org.visallo.core.config.Configuration;
import org.visallo.core.exception.VisalloAccessDeniedException;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.geocoding.DefaultGeocoderRepository;
import org.visallo.core.geocoding.GeocoderRepository;
import org.visallo.core.util.ServiceLoaderUtil;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.privilegeFilters.*;
import org.visallo.web.routes.Index;
import org.visallo.web.routes.admin.AdminList;
import org.visallo.web.routes.admin.PluginList;
import org.visallo.web.routes.dashboard.*;
import org.visallo.web.routes.directory.DirectoryGet;
import org.visallo.web.routes.directory.DirectorySearch;
import org.visallo.web.routes.edge.*;
import org.visallo.web.routes.element.ElementSearch;
import org.visallo.web.routes.extendedData.ExtendedDataGet;
import org.visallo.web.routes.extendedData.ExtendedDataSearch;
import org.visallo.web.routes.longRunningProcess.LongRunningProcessById;
import org.visallo.web.routes.longRunningProcess.LongRunningProcessCancel;
import org.visallo.web.routes.longRunningProcess.LongRunningProcessDelete;
import org.visallo.web.routes.map.GetGeocoder;
import org.visallo.web.routes.notification.Notifications;
import org.visallo.web.routes.notification.SystemNotificationDelete;
import org.visallo.web.routes.notification.SystemNotificationSave;
import org.visallo.web.routes.notification.UserNotificationMarkRead;
import org.visallo.web.routes.ontology.*;
import org.visallo.web.routes.ping.Ping;
import org.visallo.web.routes.ping.PingStats;
import org.visallo.web.routes.product.*;
import org.visallo.web.routes.resource.MapMarkerImage;
import org.visallo.web.routes.resource.ResourceExternalGet;
import org.visallo.web.routes.resource.ResourceGet;
import org.visallo.web.routes.search.*;
import org.visallo.web.routes.user.*;
import org.visallo.web.routes.vertex.*;
import org.visallo.web.routes.workspace.*;
import org.visallo.web.webEventListeners.WebEventListener;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.vertexium.util.IterableUtils.toList;

public class Router extends HttpServlet {
    private static final long serialVersionUID = 4689515508877380905L;
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(Router.class);
    private WebApp app;
    private Configuration configuration;
    private GeocoderRepository geocoderRepository;
    private List<WebEventListener> webEventListeners;
    private List<WebEventListener> webEventListenersReverse;

    @SuppressWarnings("unchecked")
    public Router(ServletContext servletContext) {
        try {
            final Injector injector = (Injector) servletContext.getAttribute(Injector.class.getName());
            injector.injectMembers(this);

            app = new WebApp(servletContext, injector);

            AuthenticationHandler authenticatorInstance = new AuthenticationHandler();
            Class<? extends Handler> authenticator = AuthenticationHandler.class;

            Class<? extends Handler> csrfProtector = VisalloCsrfHandler.class;

            app.get("/", UserAgentFilter.class, csrfProtector, Index.class);
            app.get("/configuration", csrfProtector, org.visallo.web.routes.config.Configuration.class);
            app.post("/logout", csrfProtector, Logout.class);

            app.get("/ontology", authenticator, csrfProtector, ReadPrivilegeFilter.class, Ontology.class);
            app.get("/ontology/segment", authenticator, csrfProtector, ReadPrivilegeFilter.class, OntologyGet.class);
            app.post("/ontology/concept", authenticator, csrfProtector, OntologyAddPrivilegeFilter.class, OntologyConceptSave.class);
            app.post("/ontology/property", authenticator, csrfProtector, OntologyAddPrivilegeFilter.class, OntologyPropertySave.class);
            app.post("/ontology/relationship", authenticator, csrfProtector, OntologyAddPrivilegeFilter.class, OntologyRelationshipSave.class);

            app.get("/notification/all", authenticator, csrfProtector, ReadPrivilegeFilter.class, Notifications.class);
            app.post("/notification/mark-read", authenticator, csrfProtector, ReadPrivilegeFilter.class, UserNotificationMarkRead.class);
            app.post("/notification/system", authenticator, csrfProtector, AdminPrivilegeFilter.class, SystemNotificationSave.class);
            app.delete("/notification/system", authenticator, csrfProtector, AdminPrivilegeFilter.class, SystemNotificationDelete.class);

            app.get("/resource", authenticator, csrfProtector, ReadPrivilegeFilter.class, ResourceGet.class);
            app.get("/resource/external", authenticator, csrfProtector, ReadPrivilegeFilter.class, ResourceExternalGet.class);
            app.get("/map/marker/image", csrfProtector, MapMarkerImage.class);  // TODO combine with /resource

            if (!(geocoderRepository instanceof DefaultGeocoderRepository)) {
                configuration.set(Configuration.WEB_GEOCODER_ENABLED, true);
                app.get("/map/geocode", authenticator, GetGeocoder.class);
            }

            app.post("/search/save", authenticator, csrfProtector, SearchSave.class);
            app.get("/search/all", authenticator, csrfProtector, SearchList.class);
            app.get("/search", authenticator, csrfProtector, SearchGet.class);
            app.get("/search/run", authenticator, csrfProtector, SearchRun.class);
            app.post("/search/run", authenticator, csrfProtector, SearchRun.class);
            app.delete("/search", authenticator, csrfProtector, SearchDelete.class);

            app.get("/element/search", authenticator, csrfProtector, ReadPrivilegeFilter.class, ElementSearch.class);
            app.post("/element/search", authenticator, csrfProtector, ReadPrivilegeFilter.class, ElementSearch.class);

            app.delete("/vertex", authenticator, csrfProtector, EditPrivilegeFilter.class, VertexRemove.class);
            app.get("/vertex/highlighted-text", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexHighlightedText.class);
            app.get("/vertex/raw", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexRaw.class);
            app.get("/vertex/exists", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexExists.class);
            app.post("/vertex/exists", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexExists.class);
            app.get("/vertex/thumbnail", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexThumbnail.class);
            app.get("/vertex/poster-frame", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexPosterFrame.class);
            app.get("/vertex/video-preview", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexVideoPreviewImage.class);
            app.get("/vertex/details", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexDetails.class);
            app.get("/vertex/history", authenticator, csrfProtector, HistoryReadPrivilegeFilter.class, VertexGetHistory.class);
            app.get("/vertex/property/details", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexPropertyDetails.class);
            app.post("/vertex/import", authenticator, csrfProtector, EditPrivilegeFilter.class, VertexImport.class);
            app.post("/vertex/cloudImport", authenticator, csrfProtector, EditPrivilegeFilter.class, VertexCloudImport.class);
            app.post("/vertex/resolve-term", authenticator, csrfProtector, EditPrivilegeFilter.class, ResolveTermEntity.class);
            app.post("/vertex/unresolve-term", authenticator, csrfProtector, EditPrivilegeFilter.class, UnresolveTermEntity.class);
            app.post("/vertex/resolve-detected-object", authenticator, csrfProtector, EditPrivilegeFilter.class, ResolveDetectedObject.class);
            app.post("/vertex/unresolve-detected-object", authenticator, csrfProtector, EditPrivilegeFilter.class, UnresolveDetectedObject.class);
            app.get("/vertex/detected-objects", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexGetDetectedObjects.class);
            app.get("/vertex/property", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexGetPropertyValue.class);
            app.get("/vertex/property/history", authenticator, csrfProtector, HistoryReadPrivilegeFilter.class, VertexGetPropertyHistory.class);
            app.post("/vertex/property", authenticator, csrfProtector, EditPrivilegeFilter.class, VertexSetProperty.class);
            app.post("/vertex/property/visibility", authenticator, csrfProtector, EditPrivilegeFilter.class, VertexSetPropertyVisibility.class);
            app.post("/vertex/comment", authenticator, csrfProtector, CommentPrivilegeFilter.class, VertexSetProperty.class);
            app.delete("/vertex/property", authenticator, csrfProtector, EditPrivilegeFilter.class, VertexDeleteProperty.class);
            app.delete("/vertex/comment", authenticator, csrfProtector, CommentPrivilegeFilter.class, VertexDeleteProperty.class);
            app.get("/vertex/term-mentions", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexGetTermMentions.class);
            app.get("/vertex/resolved-to", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexGetResolvedTo.class);
            app.post("/vertex/visibility", authenticator, csrfProtector, EditPrivilegeFilter.class, VertexSetVisibility.class);
            app.get("/vertex/properties", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexProperties.class);
            app.get("/vertex/edges", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexEdges.class);
            app.post("/vertex/multiple", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexMultiple.class); // this is a post method to allow large data (ie data larger than would fit in the URL)
            app.post("/vertex/new", authenticator, csrfProtector, EditPrivilegeFilter.class, VertexNew.class);
            app.get("/vertex/search", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexSearch.class);
            app.post("/vertex/search", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexSearch.class);
            app.get("/vertex/geo-search", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexGeoSearch.class);
            app.post("/vertex/upload-image", authenticator, csrfProtector, EditPrivilegeFilter.class, VertexUploadImage.class);
            app.get("/vertex/find-path", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexFindPath.class);
            app.post("/vertex/find-related", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexFindRelated.class);
            app.get("/vertex/counts-by-concept-type", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexGetCountsByConceptType.class);
            app.get("/vertex/count", authenticator, csrfProtector, ReadPrivilegeFilter.class, VertexGetCount.class);

            app.post("/edge/property", authenticator, csrfProtector, EditPrivilegeFilter.class, EdgeSetProperty.class);
            app.post("/edge/property/visibility", authenticator, csrfProtector, EditPrivilegeFilter.class, EdgeSetPropertyVisibility.class);
            app.post("/edge/comment", authenticator, csrfProtector, CommentPrivilegeFilter.class, EdgeSetProperty.class);
            app.delete("/edge", authenticator, csrfProtector, EditPrivilegeFilter.class, EdgeDelete.class);
            app.delete("/edge/property", authenticator, csrfProtector, EditPrivilegeFilter.class, EdgeDeleteProperty.class);
            app.delete("/edge/comment", authenticator, csrfProtector, CommentPrivilegeFilter.class, EdgeDeleteProperty.class);
            app.get("/edge/history", authenticator, csrfProtector, HistoryReadPrivilegeFilter.class, EdgeGetHistory.class);
            app.get("/edge/property/history", authenticator, csrfProtector, HistoryReadPrivilegeFilter.class, EdgeGetPropertyHistory.class);
            app.get("/edge/exists", authenticator, csrfProtector, ReadPrivilegeFilter.class, EdgeExists.class);
            app.post("/edge/exists", authenticator, csrfProtector, ReadPrivilegeFilter.class, EdgeExists.class);
            app.post("/edge/multiple", authenticator, csrfProtector, ReadPrivilegeFilter.class, EdgeMultiple.class);
            app.post("/edge/create", authenticator, csrfProtector, EditPrivilegeFilter.class, EdgeCreate.class);
            app.get("/edge/properties", authenticator, csrfProtector, ReadPrivilegeFilter.class, EdgeProperties.class);
            app.post("/edge/visibility", authenticator, csrfProtector, EditPrivilegeFilter.class, EdgeSetVisibility.class);
            app.get("/edge/property/details", authenticator, csrfProtector, ReadPrivilegeFilter.class, EdgePropertyDetails.class);
            app.get("/edge/details", authenticator, csrfProtector, ReadPrivilegeFilter.class, EdgeDetails.class);
            app.get("/edge/count", authenticator, csrfProtector, ReadPrivilegeFilter.class, EdgeGetCount.class);
            app.get("/edge/search", authenticator, csrfProtector, ReadPrivilegeFilter.class, EdgeSearch.class);
            app.post("/edge/search", authenticator, csrfProtector, ReadPrivilegeFilter.class, EdgeSearch.class);

            app.get("/extended-data", authenticator, csrfProtector, ReadPrivilegeFilter.class, ExtendedDataGet.class);
            app.get("/extended-data/search", authenticator, csrfProtector, ReadPrivilegeFilter.class, ExtendedDataSearch.class);
            app.post("/extended-data/search", authenticator, csrfProtector, ReadPrivilegeFilter.class, ExtendedDataSearch.class);

            app.get("/workspace/all", authenticator, csrfProtector, ReadPrivilegeFilter.class, WorkspaceList.class);
            app.post("/workspace/create", authenticator, csrfProtector, ReadPrivilegeFilter.class, WorkspaceCreate.class);
            app.get("/workspace/diff", authenticator, csrfProtector, ReadPrivilegeFilter.class, WorkspaceDiff.class);
            app.post("/workspace/update", authenticator, csrfProtector, ReadPrivilegeFilter.class, WorkspaceUpdate.class);
            app.get("/workspace", authenticator, csrfProtector, ReadPrivilegeFilter.class, WorkspaceById.class);
            app.delete("/workspace", authenticator, csrfProtector, ReadPrivilegeFilter.class, WorkspaceDelete.class);
            app.post("/workspace/publish", authenticator, csrfProtector, PublishPrivilegeFilter.class, WorkspacePublish.class);
            app.post("/workspace/undo", authenticator, csrfProtector, EditPrivilegeFilter.class, WorkspaceUndo.class);

            app.get("/dashboard/all", authenticator, csrfProtector, ReadPrivilegeFilter.class, DashboardAll.class);
            app.post("/dashboard", authenticator, csrfProtector, ReadPrivilegeFilter.class, DashboardUpdate.class);
            app.delete("/dashboard", authenticator, csrfProtector, ReadPrivilegeFilter.class, DashboardDelete.class);
            app.post("/dashboard/item", authenticator, csrfProtector, ReadPrivilegeFilter.class, DashboardItemUpdate.class);
            app.delete("/dashboard/item", authenticator, csrfProtector, ReadPrivilegeFilter.class, DashboardItemDelete.class);

            app.get("/product/all", authenticator, csrfProtector, ReadPrivilegeFilter.class, ProductAll.class);
            app.get("/product", authenticator, csrfProtector, ReadPrivilegeFilter.class, ProductGet.class);
            app.get("/product/preview", authenticator, csrfProtector, ReadPrivilegeFilter.class, ProductPreview.class);
            app.post("/product", authenticator, csrfProtector, EditPrivilegeFilter.class, ProductUpdate.class);
            app.delete("/product", authenticator, csrfProtector, EditPrivilegeFilter.class, ProductDelete.class);

            app.get("/user/me", authenticator, csrfProtector, MeGet.class);
            app.get("/user/heartbeat", authenticator, csrfProtector, Heartbeat.class);
            app.post("/user/ui-preferences", authenticator, csrfProtector, UserSetUiPreferences.class);
            app.get("/user/all", authenticator, csrfProtector, UserList.class);
            app.post("/user/all", authenticator, csrfProtector, UserList.class);
            app.get("/user", authenticator, csrfProtector, AdminPrivilegeFilter.class, UserGet.class);

            app.get("/directory/get", authenticator, csrfProtector, DirectoryGet.class);
            app.get("/directory/search", authenticator, csrfProtector, DirectorySearch.class);

            app.get("/long-running-process", authenticator, csrfProtector, LongRunningProcessById.class);
            app.delete("/long-running-process", authenticator, csrfProtector, LongRunningProcessDelete.class);
            app.post("/long-running-process/cancel", authenticator, csrfProtector, LongRunningProcessCancel.class);

            app.get("/admin/all", authenticator, csrfProtector, AdminPrivilegeFilter.class, AdminList.class);
            app.get("/admin/plugins", authenticator, csrfProtector, AdminPrivilegeFilter.class, PluginList.class);

            app.get("/ping", RateLimitFilter.class, Ping.class);
            app.get("/ping/stats", authenticator, AdminPrivilegeFilter.class, PingStats.class);

            app.post("/csp-report", ContentSecurityPolicyReport.class);

            List<WebAppPlugin> webAppPlugins = toList(ServiceLoaderUtil.load(WebAppPlugin.class, configuration));
            for (WebAppPlugin webAppPlugin : webAppPlugins) {
                LOGGER.info("Loading webapp plugin: %s", webAppPlugin.getClass().getName());
                try {
                    webAppPlugin.init(app, servletContext, authenticatorInstance);
                } catch (Exception e) {
                    throw new VisalloException("Could not initialize webapp plugin: " + webAppPlugin.getClass().getName(), e);
                }
            }

            app.get(
                    "/css/images/ui-icons_222222_256x240.png",
                    new StaticResourceHandler(
                            this.getClass(),
                            "/org/visallo/web/routes/resource/ui-icons_222222_256x240.png",
                            "image/png"
                    )
            );

            app.onException(VisalloAccessDeniedException.class, new ErrorCodeHandler(HttpServletResponse.SC_FORBIDDEN));
        } catch (Exception ex) {
            LOGGER.error("Failed to initialize Router", ex);
            throw new RuntimeException("Failed to initialize " + getClass().getName(), ex);
        }
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        LOGGER.debug("servicing %s", request.getRequestURI());
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");

        try {
            for (WebEventListener webEventListener : getWebEventListeners()) {
                webEventListener.before(app, request, response);
            }
            response.addHeader("Accept-Ranges", "bytes");
            app.handle(request, response);
            for (WebEventListener webEventListener : getWebEventListenersReverse()) {
                webEventListener.after(app, request, response);
            }
        } catch (ConnectionClosedException cce) {
            LOGGER.debug("Connection closed by client", cce);
            for (WebEventListener webEventListener : getWebEventListenersReverse()) {
                webEventListener.error(app, request, response, cce);
            }
        } catch (Throwable e) {
            for (WebEventListener webEventListener : getWebEventListenersReverse()) {
                webEventListener.error(app, request, response, e);
            }
        } finally {
            for (WebEventListener webEventListener : getWebEventListenersReverse()) {
                webEventListener.always(app, request, response);
            }
        }
    }

    private List<WebEventListener> getWebEventListeners() {
        if (webEventListeners == null) {
            webEventListeners = InjectHelper.getInjectedServices(WebEventListener.class, configuration).stream()
                    .sorted(Comparator.comparingInt(WebEventListener::getPriority))
                    .collect(Collectors.toList());
        }
        return webEventListeners;
    }

    private List<WebEventListener> getWebEventListenersReverse() {
        if (webEventListenersReverse == null) {
            webEventListenersReverse = Lists.reverse(getWebEventListeners());
        }
        return webEventListenersReverse;
    }

    @Inject
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @Inject
    public void setGeocoderRepository(GeocoderRepository geocoderRepository) {
        this.geocoderRepository = geocoderRepository;
    }

    public WebApp getApp() {
        return app;
    }
}
