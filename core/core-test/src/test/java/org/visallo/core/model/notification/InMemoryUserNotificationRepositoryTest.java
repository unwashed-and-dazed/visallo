package org.visallo.core.model.notification;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Date;

@RunWith(MockitoJUnitRunner.class)
public class InMemoryUserNotificationRepositoryTest extends UserNotificationRepositoryTestBase {
    private InMemoryUserNotificationRepository userNotificationRepository;

    @Before
    public void before() throws Exception {
        super.before();
        userNotificationRepository = new InMemoryUserNotificationRepository(
                getGraph(),
                getGraphRepository(),
                getGraphAuthorizationRepository(),
                getWorkQueueRepository()
        );
    }

    @Override
    protected InMemoryUserNotificationRepository getUserNotificationRepository() {
        return userNotificationRepository;
    }

    @Override
    protected void createNotification(
            String userId,
            String title,
            String message,
            String actionEvent,
            JSONObject actionPayload,
            Date sentTime,
            ExpirationAge expirationAge
    ) {
        getUserNotificationRepository().createNotification(
                userId,
                title,
                message,
                actionEvent,
                actionPayload,
                sentTime,
                expirationAge,
                getUserRepository().getSystemUser()
        );
    }
}
