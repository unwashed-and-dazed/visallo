package org.visallo.web.routes.user;

import com.google.inject.Singleton;
import org.visallo.web.VisalloResponse;
import org.visallo.web.clientapi.model.ClientApiSuccess;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;

/**
 * Called by client to keep the auth token valid when there is
 * user activity, but no requests to update it.
 *
 * @see "webapp/js/data/web-worker/handlers/userActivityExtend.js"
 */
@Singleton
public class Heartbeat implements ParameterizedHandler {

    @Handle
    public ClientApiSuccess handle() throws Exception {
        return VisalloResponse.SUCCESS;
    }
}
