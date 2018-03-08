package org.visallo.core.model.notification;

import org.json.JSONObject;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.visallo.core.simpleorm.SimpleOrmTestHelper;

import java.util.Date;

@RunWith(MockitoJUnitRunner.class)
public class SimpleOrmUserNotificationRepositoryTest extends UserNotificationRepositoryTestBase {
    private SimpleOrmUserNotificationRepository userNotificationRepository;

    @Override
    public void before() throws Exception {
        super.before();
        SimpleOrmTestHelper helper = new SimpleOrmTestHelper(getAuthorizationRepository());
        userNotificationRepository = new SimpleOrmUserNotificationRepository(
                getGraph(),
                getGraphRepository(),
                getGraphAuthorizationRepository(),
                helper.getSimpleOrmSession(),
                helper.getSimpleOrmContextProvider(),
                getWorkQueueRepository()
        );
    }

    @Override
    protected SimpleOrmUserNotificationRepository getUserNotificationRepository() {
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