package org.visallo.core.model.notification;

import com.google.inject.Inject;
import org.json.JSONObject;
import org.vertexium.Graph;
import org.visallo.core.bootstrap.InjectHelper;
import org.visallo.core.model.graph.GraphRepository;
import org.visallo.core.model.user.GraphAuthorizationRepository;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.user.User;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public abstract class UserNotificationRepository extends NotificationRepository {
    private UserRepository userRepository;

    @Inject
    public UserNotificationRepository(
            Graph graph,
            GraphRepository graphRepository,
            GraphAuthorizationRepository graphAuthorizationRepository
    ) {
        super(graph, graphRepository, graphAuthorizationRepository);
    }

    public Stream<UserNotification> getActiveNotifications(User user) {
        Date now = new Date();
        return findAll(user)
                .filter(notification ->
                        user.getUserId().equals(notification.getUserId())
                                && notification.getSentDate().before(now)
                                && notification.isActive()
                );
    }

    protected abstract Stream<UserNotification> findAll(User authUser);

    public Stream<UserNotification> getActiveNotificationsOlderThan(int duration, TimeUnit timeUnit, User authUser) {
        Date now = new Date();
        return findAll(authUser)
                .filter(notification -> {
                            if (!notification.isActive()) {
                                return false;
                            }
                            Date t = new Date(notification.getSentDate().getTime() + timeUnit.toMillis(duration));
                            return t.before(now);
                        }
                );
    }

    public UserNotification createNotification(
            String userId,
            String title,
            String message,
            String actionEvent,
            JSONObject actionPayload,
            ExpirationAge expirationAge,
            User authUser
    ) {
        UserNotification notification = new UserNotification(
                userId,
                title,
                message,
                actionEvent,
                actionPayload,
                expirationAge
        );
        saveNotification(notification, authUser);
        return notification;
    }

    public UserNotification createNotification(
            String userId,
            String title,
            String message,
            String externalUrl,
            ExpirationAge expirationAge,
            User authUser
    ) {
        UserNotification notification = new UserNotification(userId, title, message, null, null, expirationAge);
        notification.setExternalUrl(externalUrl);
        saveNotification(notification, authUser);
        return notification;
    }

    protected abstract void saveNotification(UserNotification notification, User authUser);

    public abstract UserNotification getNotification(String notificationId, User user);

    /**
     * This method only allows marking items read for the passed in user
     */
    public abstract void markRead(String[] notificationIds, User user);

    public abstract void markNotified(Iterable<String> notificationIds, User user);

    /**
     * Avoid circular reference with UserRepository
     */
    protected UserRepository getUserRepository() {
        if (userRepository == null) {
            userRepository = InjectHelper.getInstance(UserRepository.class);
        }
        return userRepository;
    }
}
