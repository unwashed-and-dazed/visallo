package org.visallo.core.model.notification;

import com.v5analytics.simpleorm.Entity;
import com.v5analytics.simpleorm.Field;

import java.util.Date;

@Entity(tableName = "systemNotifications")
public class SimpleOrmSystemNotification extends SimpleOrmNotification {
    @Field
    private SystemNotificationSeverity severity;

    @Field
    private Date startDate;

    @Field
    private Date endDate;

    // Used by SimpleOrm to create instance
    @SuppressWarnings("UnusedDeclaration")
    protected SimpleOrmSystemNotification() {

    }

    public SimpleOrmSystemNotification(SystemNotification notification) {
        id = notification.getId();
        title = notification.getTitle();
        message = notification.getMessage();
        actionEvent = notification.getActionEvent();
        actionPayload = notification.getActionPayload();
        severity = notification.getSeverity();
        startDate = notification.getStartDate();
        endDate = notification.getEndDate();
    }

    public static SystemNotification toSystemNotification(SimpleOrmSystemNotification notification) {
        if (notification == null) {
            return null;
        }
        SystemNotification result = new SystemNotification(
                notification.id,
                notification.title,
                notification.message,
                notification.actionEvent,
                notification.actionPayload
        );
        result.setSeverity(notification.severity);
        result.setStartDate(notification.startDate);
        result.setEndDate(notification.endDate);
        return result;
    }

    public Date getStartDate() {
        return startDate;
    }

    public Date getEndDate() {
        return endDate;
    }
}
