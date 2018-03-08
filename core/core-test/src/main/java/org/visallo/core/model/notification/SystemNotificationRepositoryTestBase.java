package org.visallo.core.model.notification;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloInMemoryTestBase;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

public abstract class SystemNotificationRepositoryTestBase extends VisalloInMemoryTestBase {
    @Before
    public void before() throws Exception {
        super.before();
    }

    @Override
    protected abstract SystemNotificationRepository getSystemNotificationRepository();

    @Test
    public void testCreateAndUpdate() {
        Date startDate = Date.from(ZonedDateTime.of(2017, 11, 28, 10, 12, 13, 0, ZoneId.of("GMT")).toInstant());
        Date endDate = Date.from(ZonedDateTime.of(2017, 11, 29, 10, 12, 13, 0, ZoneId.of("GMT")).toInstant());
        SystemNotification notification = getSystemNotificationRepository().createNotification(
                SystemNotificationSeverity.INFORMATIONAL,
                "notification title",
                "notification message",
                "http://visallo.com/notification/test",
                startDate,
                endDate,
                getUserRepository().getSystemUser()
        );

        notification = getSystemNotificationRepository().getNotification(notification.getId(), getUserRepository().getSystemUser());
        assertEquals(SystemNotificationSeverity.INFORMATIONAL, notification.getSeverity());
        assertEquals("notification title", notification.getTitle());
        assertEquals("notification message", notification.getMessage());
        assertEquals("EXTERNAL_URL", notification.getActionEvent());
        assertEquals("{\"url\":\"http://visallo.com/notification/test\"}", notification.getActionPayload().toString());
        assertEquals(startDate, notification.getStartDate());
        assertEquals(endDate, notification.getEndDate());

        // Update
        notification.setSeverity(SystemNotificationSeverity.WARNING);
        notification.setTitle("notification title2");
        notification.setMessage("notification message2");
        getSystemNotificationRepository().updateNotification(notification, getUserRepository().getSystemUser());

        notification = getSystemNotificationRepository().getNotification(notification.getId(), getUserRepository().getSystemUser());
        assertEquals(SystemNotificationSeverity.WARNING, notification.getSeverity());
        assertEquals("notification title2", notification.getTitle());
        assertEquals("notification message2", notification.getMessage());
        assertEquals("EXTERNAL_URL", notification.getActionEvent());
        assertEquals("{\"url\":\"http://visallo.com/notification/test\"}", notification.getActionPayload().toString());
        assertEquals(startDate, notification.getStartDate());
        assertEquals(endDate, notification.getEndDate());
    }

    @Test
    public void testGetActiveNotifications() {
        Date startDate = Date.from(ZonedDateTime.of(2017, 11, 28, 10, 12, 13, 0, ZoneId.of("GMT")).toInstant());
        getSystemNotificationRepository().createNotification(
                SystemNotificationSeverity.INFORMATIONAL,
                "notification title",
                "notification message",
                "http://visallo.com/notification/test",
                startDate,
                null,
                getUserRepository().getSystemUser()
        );

        List<SystemNotification> notifications = getSystemNotificationRepository().getActiveNotifications(getUserRepository().getSystemUser());
        assertEquals(1, notifications.size());
        SystemNotification notification = notifications.get(0);
        assertEquals(SystemNotificationSeverity.INFORMATIONAL, notification.getSeverity());
        assertEquals("notification title", notification.getTitle());
        assertEquals("notification message", notification.getMessage());
        assertEquals("EXTERNAL_URL", notification.getActionEvent());
        assertEquals("{\"url\":\"http://visallo.com/notification/test\"}", notification.getActionPayload().toString());
        assertEquals(startDate, notification.getStartDate());
        assertEquals(null, notification.getEndDate());
    }

    @Test
    public void testGetFutureNotifications() {
        Date startDate = Date.from(ZonedDateTime.of(2025, 11, 28, 10, 12, 13, 0, ZoneId.of("GMT")).toInstant());
        getSystemNotificationRepository().createNotification(
                SystemNotificationSeverity.INFORMATIONAL,
                "notification title",
                "notification message",
                "http://visallo.com/notification/test",
                startDate,
                null,
                getUserRepository().getSystemUser()
        );

        List<SystemNotification> notifications = getSystemNotificationRepository().getFutureNotifications(null, getUserRepository().getSystemUser());
        assertEquals(1, notifications.size());
        SystemNotification notification = notifications.get(0);
        assertEquals(SystemNotificationSeverity.INFORMATIONAL, notification.getSeverity());
        assertEquals("notification title", notification.getTitle());
        assertEquals("notification message", notification.getMessage());
        assertEquals("EXTERNAL_URL", notification.getActionEvent());
        assertEquals("{\"url\":\"http://visallo.com/notification/test\"}", notification.getActionPayload().toString());
        assertEquals(startDate, notification.getStartDate());
        assertEquals(null, notification.getEndDate());
    }
}
