package org.visallo.core.model.notification;

import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.properties.types.*;

public class NotificationOntology {
    /*************************************
     * BEGIN GENERATED CODE
     *************************************/

    public static final String IRI = "http://visallo.org/notification";

    public static final String SYSTEM_NOTIFICATIONS_TABLE = "http://visallo.org/notification#systemNotificationsTable";
    public static final StringVisalloExtendedData SYSTEM_NOTIFICATIONS_TABLE_ACTION_EVENT = new StringVisalloExtendedData("http://visallo.org/notification#systemNotificationsTable", "http://visallo.org/notification#actionEvent");
    public static final DateVisalloExtendedData SYSTEM_NOTIFICATIONS_TABLE_END_DATE = new DateVisalloExtendedData("http://visallo.org/notification#systemNotificationsTable", "http://visallo.org/notification#endDate");
    public static final StringVisalloExtendedData SYSTEM_NOTIFICATIONS_TABLE_MESSAGE = new StringVisalloExtendedData("http://visallo.org/notification#systemNotificationsTable", "http://visallo.org/notification#message");
    public static final DateVisalloExtendedData SYSTEM_NOTIFICATIONS_TABLE_START_DATE = new DateVisalloExtendedData("http://visallo.org/notification#systemNotificationsTable", "http://visallo.org/notification#startDate");
    public static final StringVisalloExtendedData SYSTEM_NOTIFICATIONS_TABLE_TITLE = new StringVisalloExtendedData("http://visallo.org/notification#systemNotificationsTable", "http://visallo.org/notification#title");
    public static final String USER_NOTIFICATIONS_TABLE = "http://visallo.org/notification#userNotificationsTable";
    public static final StringVisalloExtendedData USER_NOTIFICATIONS_TABLE_ACTION_EVENT = new StringVisalloExtendedData("http://visallo.org/notification#userNotificationsTable", "http://visallo.org/notification#actionEvent");
    public static final BooleanVisalloExtendedData USER_NOTIFICATIONS_TABLE_MARKED_READ = new BooleanVisalloExtendedData("http://visallo.org/notification#userNotificationsTable", "http://visallo.org/notification#markedRead");
    public static final StringVisalloExtendedData USER_NOTIFICATIONS_TABLE_MESSAGE = new StringVisalloExtendedData("http://visallo.org/notification#userNotificationsTable", "http://visallo.org/notification#message");
    public static final BooleanVisalloExtendedData USER_NOTIFICATIONS_TABLE_NOTIFIED = new BooleanVisalloExtendedData("http://visallo.org/notification#userNotificationsTable", "http://visallo.org/notification#notified");
    public static final DateVisalloExtendedData USER_NOTIFICATIONS_TABLE_SENT_DATE = new DateVisalloExtendedData("http://visallo.org/notification#userNotificationsTable", "http://visallo.org/notification#sentDate");
    public static final StringVisalloExtendedData USER_NOTIFICATIONS_TABLE_TITLE = new StringVisalloExtendedData("http://visallo.org/notification#userNotificationsTable", "http://visallo.org/notification#title");

    /*************************************
     * END GENERATED CODE
     *************************************/

    public static final JsonVisalloExtendedData SYSTEM_NOTIFICATIONS_TABLE_ACTION_PAYLOAD = new JsonVisalloExtendedData("http://visallo.org/notification#systemNotificationsTable", "http://visallo.org/notification#actionPayload");
    public static final JsonVisalloExtendedData USER_NOTIFICATIONS_TABLE_ACTION_PAYLOAD = new JsonVisalloExtendedData("http://visallo.org/notification#userNotificationsTable", "http://visallo.org/notification#actionPayload");
    public static final SystemNotificationSeverityVisalloExtendedData SYSTEM_NOTIFICATIONS_TABLE_SEVERITY = new SystemNotificationSeverityVisalloExtendedData("http://visallo.org/notification#systemNotificationsTable", "http://visallo.org/notification#severity");
    public static final ExpirationAgeVisalloExtendedData USER_NOTIFICATIONS_TABLE_EXPIRATION_AGE = new ExpirationAgeVisalloExtendedData("http://visallo.org/notification#userNotificationsTable", "http://visallo.org/notification#expirationAge");

    public static class SystemNotificationSeverityVisalloExtendedData extends VisalloExtendedData<SystemNotificationSeverity, String> {
        public SystemNotificationSeverityVisalloExtendedData(String tableName, String columnName) {
            super(tableName, columnName);
        }

        @Override
        public String rawToGraph(SystemNotificationSeverity value) {
            if (value == null) {
                return null;
            }
            return value.name();
        }

        @Override
        public SystemNotificationSeverity graphToRaw(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof String) {
                return SystemNotificationSeverity.valueOf((String) value);
            }
            throw new VisalloException("Unhandled value: " + value + " (type: " + value.getClass().getName() + ")");
        }
    }

    public static class ExpirationAgeVisalloExtendedData extends VisalloExtendedData<ExpirationAge, String> {
        public ExpirationAgeVisalloExtendedData(String tableName, String columnName) {
            super(tableName, columnName);
        }

        @Override
        public String rawToGraph(ExpirationAge value) {
            if (value == null) {
                return null;
            }
            return value.toString();
        }

        @Override
        public ExpirationAge graphToRaw(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof String) {
                return ExpirationAge.parse((String) value);
            }
            throw new VisalloException("Unhandled value: " + value + " (type: " + value.getClass().getName() + ")");
        }
    }
}
