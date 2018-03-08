package org.visallo.core.model.notification;

import com.google.inject.Inject;
import org.json.JSONObject;
import org.vertexium.Graph;
import org.visallo.core.model.graph.GraphRepository;
import org.visallo.core.model.user.GraphAuthorizationRepository;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.user.User;

import java.util.Date;
import java.util.List;

public abstract class SystemNotificationRepository extends NotificationRepository {
    private final UserRepository userRepository;

    @Inject
    public SystemNotificationRepository(
            Graph graph,
            GraphRepository graphRepository,
            GraphAuthorizationRepository graphAuthorizationRepository,
            UserRepository userRepository
    ) {
        super(graph, graphRepository, graphAuthorizationRepository);
        this.userRepository = userRepository;
    }

    public abstract List<SystemNotification> getActiveNotifications(User user);

    public abstract SystemNotification createNotification(
            SystemNotificationSeverity severity,
            String title,
            String message,
            String actionEvent,
            JSONObject actionPayload,
            Date startDate,
            Date endDate,
            User user
    );

    public SystemNotification createNotification(
            SystemNotificationSeverity severity,
            String title,
            String message,
            String externalUrl,
            Date startDate,
            Date endDate,
            User user
    ) {
        String actionEvent = null;
        JSONObject actionPayload = null;

        if (externalUrl != null) {
            actionEvent = Notification.ACTION_EVENT_EXTERNAL_URL;
            actionPayload = new JSONObject();
            actionPayload.put("url", externalUrl);
        }

        return createNotification(severity, title, message, actionEvent, actionPayload, startDate, endDate, user);
    }

    public abstract SystemNotification getNotification(String id, User user);

    public abstract SystemNotification updateNotification(SystemNotification notification, User user);

    public void endNotification(SystemNotification notification, User user) {
        notification.setEndDate(new Date());
        updateNotification(notification, user);
    }

    public abstract List<SystemNotification> getFutureNotifications(Date maxDate, User user);

    protected UserRepository getUserRepository() {
        return userRepository;
    }
}
