package org.visallo.core.model.notification;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InMemorySystemNotificationRepositoryTest extends SystemNotificationRepositoryTestBase {
    private InMemorySystemNotificationRepository systemNotificationRepository;

    @Before
    public void before() throws Exception {
        super.before();
        systemNotificationRepository = new InMemorySystemNotificationRepository(
                getGraph(),
                getGraphRepository(),
                getGraphAuthorizationRepository(),
                getUserRepository()
        );
    }

    @Override
    protected SystemNotificationRepository getSystemNotificationRepository() {
        return systemNotificationRepository;
    }
}
