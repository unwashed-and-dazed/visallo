package org.visallo.core.process;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.core.config.Configurable;
import org.visallo.core.config.Configuration;
import org.visallo.core.model.longRunningProcess.LongRunningProcessRunner;
import org.visallo.core.util.*;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class LongRunningProcessRunnerProcess implements VisalloProcess, ShutdownListener {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(LongRunningProcessRunnerProcess.class);
    private final Configuration configuration;
    private final Config config;
    private final List<StoppableRunnable> stoppables = new ArrayList<>();

    public static class Config {
        @Configurable
        public int threadCount;
    }

    @Inject
    public LongRunningProcessRunnerProcess(Configuration configuration, ShutdownService shutdownService) {
        this.configuration = configuration;
        this.config = configuration.setConfigurables(new Config(), LongRunningProcessRunnerProcess.class.getName());
        shutdownService.register(this);
    }

    @Override
    public void startProcess(VisalloProcessOptions options) {
        if (config.threadCount <= 0) {
            LOGGER.info("'threadCount' not configured or was 0");
            return;
        }

        stoppables.addAll(LongRunningProcessRunner.startThreaded(config.threadCount, configuration));
    }

    @Override
    public void shutdown() {
        stoppables.forEach(StoppableRunnable::stop);
    }
}
