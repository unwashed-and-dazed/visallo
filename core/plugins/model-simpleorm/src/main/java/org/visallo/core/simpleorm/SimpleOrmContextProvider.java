package org.visallo.core.simpleorm;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.v5analytics.simpleorm.SimpleOrmContext;
import com.v5analytics.simpleorm.SimpleOrmSession;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.user.User;

import java.util.Set;

@Singleton
public class SimpleOrmContextProvider {
    private final AuthorizationRepository authorizationRepository;
    private final SimpleOrmSession simpleOrmSession;

    @Inject
    public SimpleOrmContextProvider(
            AuthorizationRepository authorizationRepository,
            SimpleOrmSession simpleOrmSession
    ) {
        this.authorizationRepository = authorizationRepository;
        this.simpleOrmSession = simpleOrmSession;
    }

    public SimpleOrmContext getContext(User user) {
        Set<String> authorizationsSet = authorizationRepository.getAuthorizations(user);
        String[] authorizations = authorizationsSet.toArray(new String[authorizationsSet.size()]);
        return getContext(authorizations);
    }

    public SimpleOrmContext getContext(String... authorizations) {
        return simpleOrmSession.createContext(authorizations);
    }
}
