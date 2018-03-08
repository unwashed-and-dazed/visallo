package org.visallo.core.model.notification;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.v5analytics.simpleorm.SimpleOrmContext;
import com.v5analytics.simpleorm.SimpleOrmSession;
import org.json.JSONObject;
import org.vertexium.Graph;
import org.visallo.core.model.graph.GraphRepository;
import org.visallo.core.model.user.GraphAuthorizationRepository;
import org.visallo.core.model.workQueue.WorkQueueRepository;
import org.visallo.core.simpleorm.SimpleOrmContextProvider;
import org.visallo.core.user.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.visallo.core.util.StreamUtil.stream;

@Singleton
public class SimpleOrmUserNotificationRepository extends UserNotificationRepository {
    private final SimpleOrmSession simpleOrmSession;
    private final SimpleOrmContextProvider simpleOrmContextProvider;
    private WorkQueueRepository workQueueRepository;

    @Inject
    public SimpleOrmUserNotificationRepository(
            Graph graph,
            GraphRepository graphRepository,
            GraphAuthorizationRepository graphAuthorizationRepository,
            SimpleOrmSession simpleOrmSession,
            SimpleOrmContextProvider simpleOrmContextProvider,
            WorkQueueRepository workQueueRepository
    ) {
        super(graph, graphRepository, graphAuthorizationRepository);
        this.simpleOrmSession = simpleOrmSession;
        this.simpleOrmContextProvider = simpleOrmContextProvider;
        this.workQueueRepository = workQueueRepository;
    }

    @Override
    protected Stream<UserNotification> findAll(User authUser) {
        SimpleOrmContext ctx = simpleOrmContextProvider.getContext(authUser);
        return stream(simpleOrmSession.findAll(SimpleOrmUserNotification.class, ctx))
                .map(SimpleOrmUserNotification::toUserNotification);
    }

    @Override
    protected void saveNotification(UserNotification notification, User authUser) {
        simpleOrmSession.save(new SimpleOrmUserNotification(notification), VISIBILITY_STRING, simpleOrmContextProvider.getContext(authUser));
        workQueueRepository.pushUserNotification(notification);
    }

    @Override
    public UserNotification getNotification(String notificationId, User user) {
        return SimpleOrmUserNotification.toUserNotification(simpleOrmSession.findById(
                SimpleOrmUserNotification.class,
                notificationId,
                simpleOrmContextProvider.getContext(user)
        ));
    }

    @Override
    public void markRead(String[] notificationIds, User user) {
        Collection<SimpleOrmUserNotification> toSave = new ArrayList<>();
        for (String notificationId : notificationIds) {
            UserNotification notification = getNotification(notificationId, user);
            checkNotNull(notification, "Could not find notification with id " + notificationId);
            notification.setMarkedRead(true);
            toSave.add(new SimpleOrmUserNotification(notification));
        }
        simpleOrmSession.saveMany(toSave, VISIBILITY_STRING, simpleOrmContextProvider.getContext(user));
    }

    @Override
    public void markNotified(Iterable<String> notificationIds, User user) {
        Collection<SimpleOrmUserNotification> toSave = new ArrayList<>();
        for (String notificationId : notificationIds) {
            UserNotification notification = getNotification(notificationId, user);
            checkNotNull(notification, "Could not find notification with id " + notificationId);
            notification.setNotified(true);
            toSave.add(new SimpleOrmUserNotification(notification));
        }
        simpleOrmSession.saveMany(toSave, VISIBILITY_STRING, simpleOrmContextProvider.getContext(user));
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
