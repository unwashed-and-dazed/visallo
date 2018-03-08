package org.visallo.core.model.notification;

import com.google.common.collect.Lists;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloInMemoryTestBase;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public abstract class UserNotificationRepositoryTestBase extends VisalloInMemoryTestBase {
    private User user1;
    private User user2;

    @Before
    public void before() throws Exception {
        super.before();
        user1 = getUserRepository().findOrAddUser("user1", "user1", null, "pass");
        user2 = getUserRepository().findOrAddUser("user2", "user2", null, "pass");
    }

    @Override
    protected abstract UserNotificationRepository getUserNotificationRepository();

    protected abstract void createNotification(
            String userId,
            String title,
            String message,
            String actionEvent,
            JSONObject actionPayload,
            Date sentTime,
            ExpirationAge expirationAge
    );

    @Test
    public void testCreate() {
        Date now = new Date();

        createNotification(
                user1.getUserId(),
                "title 1",
                "Message1",
                null,
                null,
                new Date(now.getTime() - 1000),
                new ExpirationAge(10, ExpirationAgeUnit.MINUTE)
        );

        List<UserNotification> notifications = getUserNotificationRepository().findAll(getUserRepository().getSystemUser())
                .collect(Collectors.toList());
        assertEquals(1, notifications.size());
        assertEquals(1, getBroadcastJsonValues().size());
        UserNotification notification = notifications.get(0);
        assertEquals("title 1", notification.getTitle());
        assertEquals("Message1", notification.getMessage());
        assertEquals(false, notification.isMarkedRead());
        assertEquals(false, notification.isNotified());
        assertEquals(true, notification.isActive());

        notification = getUserNotificationRepository().getNotification(notification.getId(), user1);
        assertEquals("title 1", notification.getTitle());

        getUserNotificationRepository().markNotified(Lists.newArrayList(notification.getId()), user1);
        notification = getUserNotificationRepository().getNotification(notification.getId(), user1);
        assertEquals(true, notification.isNotified());
        assertEquals(false, notification.isMarkedRead());

        getUserNotificationRepository().markRead(new String[]{notification.getId()}, user1);
        notification = getUserNotificationRepository().getNotification(notification.getId(), user1);
        assertEquals(true, notification.isNotified());
        assertEquals(true, notification.isMarkedRead());
    }

    @Test
    public void testGetActiveNotifications() {
        Date now = new Date();

        createNotification(
                user1.getUserId(),
                "Expired",
                "Message1",
                null,
                null,
                new Date(now.getTime() - (2 * 60 * 1000)),
                new ExpirationAge(1, ExpirationAgeUnit.MINUTE)
        );

        createNotification(
                user1.getUserId(),
                "Current",
                "Message 2",
                null,
                null,
                new Date(now.getTime() - 1000),
                new ExpirationAge(1, ExpirationAgeUnit.MINUTE)
        );

        createNotification(
                user2.getUserId(),
                "Other User's",
                "Message 3",
                null,
                null,
                new Date(now.getTime() - 1000),
                new ExpirationAge(1, ExpirationAgeUnit.MINUTE)
        );

        List<UserNotification> activeNotifications = getUserNotificationRepository().getActiveNotifications(user1)
                .collect(Collectors.toList());
        assertEquals(1, activeNotifications.size());
        assertEquals("Current", activeNotifications.get(0).getTitle());
    }

    @Test
    public void testGetActiveNotificationsInDateRange() {
        List<UserNotification> activeNotifications;
        long currentTime = new Date().getTime();

        createNotification(user1.getUserId(), "t-120", "Message 1", null, null, new Date(currentTime - 120000), null);
        createNotification(user1.getUserId(), "t-60", "Message 2", null, null, new Date(currentTime - 60000), null);
        createNotification(user1.getUserId(), "t-30", "Message 3", null, null, new Date(currentTime - 30000), null);

        activeNotifications = getUserNotificationRepository().getActiveNotificationsOlderThan(80, TimeUnit.SECONDS, user2)
                .collect(Collectors.toList());
        assertEquals(1, activeNotifications.size());
        assertEquals("t-120", activeNotifications.get(0).getTitle());

        activeNotifications = getUserNotificationRepository().getActiveNotificationsOlderThan(45, TimeUnit.SECONDS, user2)
                .collect(Collectors.toList());
        assertEquals(2, activeNotifications.size());
        assertEquals("t-120", activeNotifications.get(0).getTitle());
        assertEquals("t-60", activeNotifications.get(1).getTitle());

        activeNotifications = getUserNotificationRepository().getActiveNotificationsOlderThan(10, TimeUnit.SECONDS, user2)
                .collect(Collectors.toList());
        assertEquals(3, activeNotifications.size());
        assertEquals("t-120", activeNotifications.get(0).getTitle());
        assertEquals("t-60", activeNotifications.get(1).getTitle());
        assertEquals("t-30", activeNotifications.get(2).getTitle());
    }
}
