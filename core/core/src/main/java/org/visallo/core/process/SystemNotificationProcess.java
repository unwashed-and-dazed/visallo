package org.visallo.core.process;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.core.model.notification.SystemNotificationService;

@Singleton
public class SystemNotificationProcess implements VisalloProcess {
    private final SystemNotificationService systemNotificationService;

    @Inject
    public SystemNotificationProcess(SystemNotificationService systemNotificationService) {
        this.systemNotificationService = systemNotificationService;
    }

    @Override
    public void startProcess(VisalloProcessOptions options) {
        this.systemNotificationService.start();
    }
}
