package org.visallo.core.simpleorm;

import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.v5analytics.simpleorm.SimpleOrmSession;
import org.visallo.core.bootstrap.BootstrapBindingProvider;
import org.visallo.core.bootstrap.InjectHelper;
import org.visallo.core.bootstrap.VisalloBootstrap;
import org.visallo.core.config.Configuration;
import org.visallo.core.util.ShutdownService;

public class SimpleOrmBootstrapBindingProvider implements BootstrapBindingProvider {
    @Override
    public void addBindings(Binder binder, Configuration configuration) {
        binder.bind(SimpleOrmSession.class)
                .toProvider(getSimpleOrmSessionProvider(configuration, SimpleOrmConfiguration.SIMPLE_ORM_SESSION))
                .in(Scopes.SINGLETON);
    }

    private Provider<? extends SimpleOrmSession> getSimpleOrmSessionProvider(
            Configuration configuration,
            String simpleOrmSessionConfigurationName
    ) {
        return (Provider<SimpleOrmSession>) () -> {
            Provider<? extends SimpleOrmSession> provider = VisalloBootstrap.getConfigurableProvider(
                    configuration,
                    simpleOrmSessionConfigurationName
            );
            SimpleOrmSession simpleOrmSession = provider.get();
            getShutdownService().register(new SimpleOrmSessionShutdownListener(simpleOrmSession));
            return simpleOrmSession;
        };
    }

    private ShutdownService getShutdownService() {
        return InjectHelper.getInstance(ShutdownService.class);
    }
}
