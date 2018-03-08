package org.visallo.core.model.notification;

import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.visallo.core.simpleorm.SimpleOrmTestHelper;

@RunWith(MockitoJUnitRunner.class)
public class SimpleOrmSystemNotificationRepositoryTest extends SystemNotificationRepositoryTestBase {
    private SimpleOrmSystemNotificationRepository systemNotificationRepository;

    @Override
    public void before() throws Exception {
        super.before();
        SimpleOrmTestHelper helper = new SimpleOrmTestHelper(getAuthorizationRepository());
        systemNotificationRepository = new SimpleOrmSystemNotificationRepository(
                getGraph(),
                getGraphRepository(),
                getGraphAuthorizationRepository(),
                getUserRepository(),
                helper.getSimpleOrmSession(),
                helper.getSimpleOrmContextProvider()
        );
    }

    @Override
    protected SimpleOrmSystemNotificationRepository getSystemNotificationRepository() {
        return systemNotificationRepository;
    }
}