package org.visallo.core.model.notification;

import org.json.JSONObject;

import java.util.Date;
import java.util.UUID;

public class SystemNotification extends Notification {
    private SystemNotificationSeverity severity;
    private Date startDate;
    private Date endDate;

    public SystemNotification(Date startDate, String title, String message, String actionEvent, JSONObject actionPayload) {
        this(createId(startDate), title, message, actionEvent, actionPayload);
    }

    public SystemNotification(String id, String title, String message, String actionEvent, JSONObject actionPayload) {
        super(id, title, message, actionEvent, actionPayload);
    }

    private static String createId(Date startDate) {
        return Long.toString(startDate.getTime()) + ":" + UUID.randomUUID().toString();
    }

    public SystemNotificationSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(SystemNotificationSeverity severity) {
        this.severity = severity;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        if (startDate == null) {
            startDate = new Date();
        }
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public boolean isActive() {
        Date now = new Date();
        Date endDate = getEndDate();
        return getStartDate().before(now) && (endDate == null || endDate.after(now));
    }

    @Override
    protected String getType() {
        return "system";
    }

    @Override
    public void populateJSONObject(JSONObject json) {
        json.put("severity", getSeverity());
        Date startDate = getStartDate();
        if (startDate != null) {
            json.put("startDate", startDate.getTime());
        }
        Date endDate = getEndDate();
        if (endDate != null) {
            json.put("endDate", endDate.getTime());
        }
    }
}
