package org.visallo.core.model.notification;

import com.v5analytics.simpleorm.Entity;
import com.v5analytics.simpleorm.Field;

import java.util.Date;

@Entity(tableName = "userNotifications")
public class SimpleOrmUserNotification extends SimpleOrmNotification {
    @Field
    private String userId;

    @Field
    private Date sentDate;

    @Field
    private Integer expirationAgeAmount;

    @Field
    private ExpirationAgeUnit expirationAgeUnit;

    @Field
    private boolean markedRead;

    @Field
    private boolean notified;

    // Used by SimpleOrm to create instance
    @SuppressWarnings("UnusedDeclaration")
    public SimpleOrmUserNotification() {

    }

    public SimpleOrmUserNotification(UserNotification notification) {
        id = notification.getId();
        title = notification.getTitle();
        message = notification.getMessage();
        actionEvent = notification.getActionEvent();
        actionPayload = notification.getActionPayload();
        userId = notification.getUserId();
        sentDate = notification.getSentDate();
        if (notification.getExpirationAge() != null) {
            expirationAgeAmount = notification.getExpirationAge().getAmount();
            expirationAgeUnit = notification.getExpirationAge().getExpirationAgeUnit();
        }
        markedRead = notification.isMarkedRead();
        notified = notification.isNotified();
    }

    public static UserNotification toUserNotification(SimpleOrmUserNotification notification) {
        if (notification == null) {
            return null;
        }
        ExpirationAge expirationAge;
        if (notification.expirationAgeAmount == null || notification.expirationAgeUnit == null) {
            expirationAge = null;
        } else {
            expirationAge = new ExpirationAge(notification.expirationAgeAmount, notification.expirationAgeUnit);
        }
        UserNotification result = new UserNotification(
                notification.id,
                notification.userId,
                notification.title,
                notification.message,
                notification.actionEvent,
                notification.actionPayload,
                notification.sentDate,
                expirationAge
        );
        result.setMarkedRead(notification.markedRead);
        result.setNotified(notification.notified);
        return result;
    }
}
