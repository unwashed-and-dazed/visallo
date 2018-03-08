package org.visallo.web;

import com.google.inject.Inject;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.PerRequestBroadcastFilter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.visallo.core.bootstrap.InjectHelper;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.JSONUtil;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;

import javax.servlet.http.HttpServletRequest;

import static com.google.common.base.Preconditions.checkNotNull;

public class MessagingFilter implements PerRequestBroadcastFilter {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(MessagingFilter.class);
    public static final String TYPE_SET_ACTIVE_WORKSPACE = "setActiveWorkspace";
    public static final String TYPE_SET_ACTIVE_PRODUCT = "setActiveProduct";
    private UserRepository userRepository;

    @Override
    public BroadcastAction filter(String broadcasterId, Object originalMessage, Object message) {
        return new BroadcastAction(message);
    }

    @Override
    public BroadcastAction filter(String broadcasterId, AtmosphereResource r, Object originalMessage, Object message) {
        ensureInitialized();

        try {
            if (message == null || r.isCancelled()) {
                return new BroadcastAction(BroadcastAction.ACTION.ABORT, null);
            }
            JSONObject json = new JSONObject(message.toString());

            if (shouldSendMessage(json, r.getRequest())) {
                return new BroadcastAction(message);
            } else {
                return new BroadcastAction(BroadcastAction.ACTION.ABORT, message);
            }
        } catch (JSONException e) {
            LOGGER.error("Failed to filter message:\n" + originalMessage, e);
            return new BroadcastAction(BroadcastAction.ACTION.ABORT, message);
        }
    }

    boolean shouldSendMessage(JSONObject json, HttpServletRequest request) {
        String type = json.optString("type", null);
        if (TYPE_SET_ACTIVE_WORKSPACE.equals(type) || TYPE_SET_ACTIVE_PRODUCT.equals(type)) {
            return false;
        }

        if (request == null) {
            return false;
        }

        return shouldSendMessageByPermissions(json, request);
    }

    private boolean shouldSendMessageByPermissions(JSONObject json, HttpServletRequest request) {
        JSONObject permissionsJson = json.optJSONObject("permissions");
        if (permissionsJson != null) {
            if (shouldRejectMessageByUsers(permissionsJson, request)) {
                return false;
            }

            if (shouldRejectMessageToWorkspaces(permissionsJson, request)) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldRejectMessageToWorkspaces(JSONObject permissionsJson, HttpServletRequest request) {
        JSONArray workspaces = permissionsJson.optJSONArray("workspaces");
        if (workspaces != null) {
            User currentUser = CurrentUser.get(request);
            if (currentUser == null) {
                return true;
            }

            String currentWorkspaceId = userRepository.getCurrentWorkspaceId(currentUser.getUserId());
            if (currentWorkspaceId == null) {
                return true;
            }

            if (!JSONUtil.isInArray(workspaces, currentWorkspaceId)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldRejectMessageByUsers(JSONObject permissionsJson, HttpServletRequest request) {
        JSONArray users = permissionsJson.optJSONArray("users");
        if (users != null) {
            User currentUser = CurrentUser.get(request);
            if (currentUser != null && currentUser.getUserId() != null && !JSONUtil.isInArray(users, currentUser.getUserId())) {
                return true;
            }
        }
        return false;
    }

    public void ensureInitialized() {
        if (userRepository == null) {
            InjectHelper.inject(this);
            if (userRepository == null) {
                LOGGER.error("userRepository cannot be null");
                checkNotNull(userRepository, "userRepository cannot be null");
            }
        }
    }

    @Inject
    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

}
