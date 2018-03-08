package org.visallo.web.routes.notification;

import org.visallo.webster.DefaultParameterValueConverter;
import org.visallo.core.model.notification.SystemNotificationSeverity;

public class SystemNotificationSeverityValueConverter extends DefaultParameterValueConverter.SingleValueConverter<SystemNotificationSeverity> {
    @Override
    public SystemNotificationSeverity convert(Class parameterType, String parameterName, String value) {
        return SystemNotificationSeverity.valueOf(value);
    }
}
