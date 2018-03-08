package org.visallo.core.externalResource;

import org.visallo.core.bootstrap.InjectHelper;
import org.visallo.core.config.Configuration;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExternalResourceRunner {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(ExternalResourceRunner.class);
    private final Configuration config;
    private final User user;
    private List<RunningWorker> runningWorkers = new ArrayList<>();

    public ExternalResourceRunner(
            Configuration config,
            final User user
    ) {
        this.config = config;
        this.user = user;
    }

    public void startAllAndWait() {
        Collection<RunningWorker> runningWorkers = startAll();
        while (runningWorkers.size() > 0) {
            for (RunningWorker runningWorker : runningWorkers) {
                if (!runningWorker.getThread().isAlive()) {
                    LOGGER.error("found a dead thread: " + runningWorker.getThread().getName());
                    return;
                }

                try {
                    runningWorker.getThread().join(1000);
                } catch (InterruptedException e) {
                    LOGGER.error("join interrupted", e);
                    return;
                }
            }
        }
    }

    public Collection<RunningWorker> startAll() {
        runningWorkers = new ArrayList<>();

        Collection<ExternalResourceWorker> workers = InjectHelper.getInjectedServices(
                ExternalResourceWorker.class,
                config
        );
        for (final ExternalResourceWorker worker : workers) {
            runningWorkers.add(start(worker, user));
        }
        return runningWorkers;
    }

    private RunningWorker start(final ExternalResourceWorker worker, final User user) {
        worker.prepare(user);
        Thread t = new Thread(() -> {
            try {
                worker.run();
            } catch (Throwable ex) {
                LOGGER.error("Failed running external resource worker: " + worker.getClass().getName(), ex);
            }
        });
        t.setName("external-resource-worker-" + worker.getClass().getSimpleName() + "-" + t.getId());
        t.setDaemon(true);
        LOGGER.debug("starting external resource worker thread: %s", t.getName());
        t.start();
        return new RunningWorker(worker, t);
    }

    public void shutdown() {
        LOGGER.debug("Stopping ExternalResourceRunner...");
        for (RunningWorker worker : runningWorkers) {
            worker.shutdown();
        }
        LOGGER.debug("Stopped ExternalResourceRunner");
    }

    public static class RunningWorker {
        private final ExternalResourceWorker worker;
        private final Thread thread;

        public RunningWorker(ExternalResourceWorker worker, Thread thread) {
            this.worker = worker;
            this.thread = thread;
        }

        public ExternalResourceWorker getWorker() {
            return worker;
        }

        public Thread getThread() {
            return thread;
        }

        public void shutdown() {
            worker.stop();
        }
    }
}
