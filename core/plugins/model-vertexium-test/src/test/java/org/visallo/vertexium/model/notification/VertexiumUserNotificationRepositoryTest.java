package org.visallo.vertexium.model.notification;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.visallo.core.model.notification.ExpirationAge;
import org.visallo.core.model.notification.UserNotificationRepositoryTestBase;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.user.UserRepository;

import java.util.Date;

@RunWith(MockitoJUnitRunner.class)
public class VertexiumUserNotificationRepositoryTest extends UserNotificationRepositoryTestBase {
    private VertexiumUserNotificationRepository userNotificationRepository;

    @Before
    public void before() throws Exception {
        super.before();
        userNotificationRepository = new VertexiumUserNotificationRepository(
                getGraph(),
                getGraphRepository(),
                getGraphAuthorizationRepository(),
                getWorkQueueRepository()
        ) {
            @Override
            protected UserRepository getUserRepository() {
                return VertexiumUserNotificationRepositoryTest.this.getUserRepository();
            }

            @Override
            protected AuthorizationRepository getAuthorizationRepository() {
                return VertexiumUserNotificationRepositoryTest.this.getAuthorizationRepository();
            }
        };
    }

    @Override
    protected VertexiumUserNotificationRepository getUserNotificationRepository() {
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
