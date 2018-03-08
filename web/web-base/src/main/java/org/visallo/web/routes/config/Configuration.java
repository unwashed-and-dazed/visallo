package org.visallo.web.routes.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.json.JSONObject;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

import java.util.ResourceBundle;

@Singleton
public class Configuration implements ParameterizedHandler {
    private final org.visallo.core.config.Configuration configuration;

    @Inject
    public Configuration(final org.visallo.core.config.Configuration configuration) {
        this.configuration = configuration;
    }

    @Handle
    public JSONObject handle(
            ResourceBundle resourceBundle,
            @ActiveWorkspaceId(required = false) String workspaceId
    ) throws Exception {
        return this.configuration.toJSON(resourceBundle, workspaceId);
    }
}
