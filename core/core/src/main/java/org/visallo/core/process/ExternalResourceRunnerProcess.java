package org.visallo.core.process;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.core.config.Configuration;
import org.visallo.core.externalResource.ExternalResourceRunner;
import org.visallo.core.util.ShutdownListener;
import org.visallo.core.util.ShutdownService;

@Singleton
public class ExternalResourceRunnerProcess implements VisalloProcess, ShutdownListener {
    private final Configuration configuration;
    private ExternalResourceRunner resourceRunner;

    @Inject
    public ExternalResourceRunnerProcess(
            Configuration configuration,
            ShutdownService shutdownService
    ) {
        this.configuration = configuration;
        shutdownService.register(this);
    }

    @Override
    public void startProcess(VisalloProcessOptions options) {
        resourceRunner = new ExternalResourceRunner(configuration, options.getUser());
        resourceRunner.startAll();
    }

    @Override
    public void shutdown() {
        if (resourceRunner != null) {
            resourceRunner.shutdown();
        }
    }
}
