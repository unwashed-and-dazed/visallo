package org.visallo.core.model.notification;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.json.JSONObject;
import org.vertexium.Graph;
import org.visallo.core.model.graph.GraphRepository;
import org.visallo.core.model.user.GraphAuthorizationRepository;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.user.User;

import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class InMemorySystemNotificationRepository extends SystemNotificationRepository {
    private final Map<String, SystemNotification> notifications = new HashMap<>();

    @Inject
    public InMemorySystemNotificationRepository(
            Graph graph,
            GraphRepository graphRepository,
            GraphAuthorizationRepository graphAuthorizationRepository,
            UserRepository userRepository
    ) {
        super(graph, graphRepository, graphAuthorizationRepository, userRepository);
    }

    @Override
    public List<SystemNotification> getActiveNotifications(User user) {
        Date now = new Date();
        return notifications.values().stream()
                .filter(n -> n.getStartDate().compareTo(now) <= 0 && (n.getEndDate() == null || n.getEndDate().compareTo(now) >= 0))
                .sorted(Comparator.comparing(SystemNotification::getStartDate))
                .collect(Collectors.toList());
    }

    @Override
    public SystemNotification createNotification(
            SystemNotificationSeverity severity,
            String title,
            String message,
            String actionEvent,
            JSONObject actionPayload,
            Date startDate,
            Date endDate,
            User user
    ) {
        if (startDate == null) {
            startDate = new Date();
        }
        SystemNotification notification = new SystemNotification(startDate, title, message, actionEvent, actionPayload);
        notification.setSeverity(severity);
        notification.setStartDate(startDate);
        notification.setEndDate(endDate);
        return updateNotification(notification, user);
    }

    @Override
    public SystemNotification getNotification(String id, User user) {
        return notifications.get(id);
    }

    @Override
    public SystemNotification updateNotification(SystemNotification notification, User user) {
        notifications.put(notification.getId(), notification);
        return notification;
    }

    @Override
    public List<SystemNotification> getFutureNotifications(Date maxDate, User user) {
        Date now = new Date();
        return notifications.values().stream()
                .filter(n -> n.getStartDate().compareTo(now) >= 0 && (n.getEndDate() == null || n.getEndDate().compareTo(maxDate) <= 0))
                .sorted(Comparator.comparing(SystemNotification::getStartDate))
                .collect(Collectors.toList());
    }
}
