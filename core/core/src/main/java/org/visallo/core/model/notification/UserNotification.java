package org.visallo.core.model.notification;

import com.google.common.annotations.VisibleForTesting;
import org.json.JSONObject;

import java.util.*;

public class UserNotification extends Notification {
    private String userId;
    private Date sentDate;
    private Integer expirationAgeAmount;
    private ExpirationAgeUnit expirationAgeUnit;
    private boolean markedRead;
    private boolean notified;

    @SuppressWarnings("UnusedDeclaration")
    protected UserNotification() {
        super();
    }

    @VisibleForTesting
    public UserNotification(
            String userId,
            String title,
            String message,
            String actionEvent,
            JSONObject actionPayload,
            ExpirationAge expirationAge
    ) {
        this(userId, title, message, actionEvent, actionPayload, new Date(), expirationAge);
    }

    @VisibleForTesting
    public UserNotification(
            String userId,
            String title,
            String message,
            String actionEvent,
            JSONObject actionPayload,
            Date sentDate,
            ExpirationAge expirationAge
    ) {
        this(createRowKey(sentDate), userId, title, message, actionEvent, actionPayload, sentDate, expirationAge);
    }

    public UserNotification(
            String id,
            String userId,
            String title,
            String message,
            String actionEvent,
            JSONObject actionPayload,
            Date sentDate,
            ExpirationAge expirationAge
    ) {
        super(id, title, message, actionEvent, actionPayload);
        this.userId = userId;
        this.sentDate = sentDate;
        this.markedRead = false;
        this.notified = false;
        if (expirationAge != null) {
            this.expirationAgeAmount = expirationAge.getAmount();
            this.expirationAgeUnit = expirationAge.getExpirationAgeUnit();
        }
    }

    private static String createRowKey(Date date) {
        return Long.toString(date.getTime()) + ":" + UUID.randomUUID().toString();
    }

    public String getUserId() {
        return userId;
    }

    public Date getSentDate() {
        return sentDate;
    }

    public ExpirationAge getExpirationAge() {
        if (expirationAgeUnit != null && expirationAgeAmount != null) {
            return new ExpirationAge(expirationAgeAmount, expirationAgeUnit);
        }
        return null;
    }

    public boolean isMarkedRead() {
        return markedRead;
    }

    public void setMarkedRead(boolean markedRead) {
        this.markedRead = markedRead;
    }

    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    public boolean isActive() {
        if (isMarkedRead()) {
            return false;
        }
        Date now = new Date();
        Date expirationDate = getExpirationDate();
        Date sentDate = getSentDate();
        return sentDate.before(now) && (expirationDate == null || expirationDate.after(now));
    }

    public Date getExpirationDate() {
        ExpirationAge age = getExpirationAge();
        if (age == null) {
            return null;
        }

        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.setTime(getSentDate());
        cal.add(age.getExpirationAgeUnit().getCalendarUnit(), age.getAmount());
        return cal.getTime();
    }

    @Override
    protected String getType() {
        return "user";
    }

    @Override
    public void populateJSONObject(JSONObject json) {
        json.put("userId", getUserId());
        json.put("sentDate", getSentDate());
        json.put("expirationAge", getExpirationAge());
        json.put("markedRead", isMarkedRead());
        json.put("notified", isNotified());
    }

    @Override
    public String toString() {
        return "UserNotification{" +
                "userId='" + userId + '\'' +
                ", title=" + getTitle() +
                ", sentDate=" + sentDate +
                ", expirationAgeAmount=" + expirationAgeAmount +
                ", expirationAgeUnit=" + expirationAgeUnit +
                ", markedRead=" + markedRead +
                ", notified=" + notified +
                '}';
    }
}
