package org.visallo.core.model.notification;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.json.JSONObject;
import org.vertexium.Graph;
import org.visallo.core.model.graph.GraphRepository;
import org.visallo.core.model.user.GraphAuthorizationRepository;
import org.visallo.core.model.workQueue.WorkQueueRepository;
import org.visallo.core.user.User;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@Singleton
public class InMemoryUserNotificationRepository extends UserNotificationRepository {
    private Map<String, UserNotification> notifications = new HashMap<>();
    private WorkQueueRepository workQueueRepository;

    @Inject
    public InMemoryUserNotificationRepository(
            Graph graph,
            GraphRepository graphRepository,
            GraphAuthorizationRepository graphAuthorizationRepository,
            WorkQueueRepository workQueueRepository
    ) {
        super(graph, graphRepository, graphAuthorizationRepository);
        this.workQueueRepository = workQueueRepository;
    }

    @Override
    protected Stream<UserNotification> findAll(User authUser) {
        return notifications.values().stream()
                .sorted(Comparator.comparing(UserNotification::getSentDate));
    }

    @Override
    protected void saveNotification(UserNotification notification, User authUser) {
        notifications.put(notification.getId(), notification);
        workQueueRepository.pushUserNotification(notification);
    }

    @Override
    public UserNotification getNotification(String id, User user) {
        return notifications.get(id);
    }

    @Override
    public void markRead(String[] notificationIds, User user) {
        for (String notificationId : notificationIds) {
            notifications.get(notificationId).setMarkedRead(true);
        }
    }

    @Override
    public void markNotified(Iterable<String> notificationIds, User user) {
        for (String notificationId : notificationIds) {
            notifications.get(notificationId).setNotified(true);
        }
    }

    public UserNotification createNotification(
            String userId,
            String title,
            String message,
            String actionEvent,
            JSONObject actionPayload,
            Date sentTime,
            ExpirationAge expirationAge,
            User authUser
    ) {
        UserNotification notification = new UserNotification(
                userId,
                title,
                message,
                actionEvent,
                actionPayload,
                sentTime,
                expirationAge
        );
        saveNotification(notification, authUser);
        return notification;
    }
}
