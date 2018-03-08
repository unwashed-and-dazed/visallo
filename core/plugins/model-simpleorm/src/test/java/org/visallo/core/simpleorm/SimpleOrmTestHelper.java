package org.visallo.core.simpleorm;

import com.v5analytics.simpleorm.InMemorySimpleOrmSession;
import com.v5analytics.simpleorm.SimpleOrmSession;
import org.visallo.core.model.user.AuthorizationRepository;

public class SimpleOrmTestHelper {
    private final InMemorySimpleOrmSession simpleOrmSession;
    private final SimpleOrmContextProvider simpleOrmContextProvider;

    public SimpleOrmTestHelper(AuthorizationRepository authorizationRepository) {
        simpleOrmSession = new InMemorySimpleOrmSession();
        simpleOrmContextProvider = new SimpleOrmContextProvider(
                authorizationRepository,
                simpleOrmSession
        );
    }

    public SimpleOrmContextProvider getSimpleOrmContextProvider() {
        return simpleOrmContextProvider;
    }

    public SimpleOrmSession getSimpleOrmSession() {
        return simpleOrmSession;
    }
}
