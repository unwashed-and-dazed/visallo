package org.visallo.core.status;

import com.codahale.metrics.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.concurrent.atomic.AtomicInteger;

public class JmxMetricsManager implements MetricsManager {
    private static final MetricRegistry REGISTRY;
    private static final JmxReporter JMX_REPORTER;
    private static final AtomicInteger ID = new AtomicInteger(0);
    private static final String DOMAIN = "visallo";

    static {
        REGISTRY = new MetricRegistry();
        JMX_REPORTER = JmxReporter.forRegistry(REGISTRY)
                .createsObjectNamesWith(new DefaultObjectNameFactory() {
                    @Override
                    public ObjectName createName(String type, String domain, String name) {
                        try {
                            return new ObjectName(name);
                        } catch (MalformedObjectNameException ex) {
                            return super.createName(type, domain, name);
                        }
                    }
                })
                .build();
        JMX_REPORTER.start();
    }

    private static int nextId() {
        return ID.getAndIncrement();
    }

    @Override
    public Counter counter(String name) {
        return REGISTRY.counter(name);
    }

    @Override
    public Counter counter(Object source, String name) {
        return counter(createMetricName(source, "counter", name));
    }

    @Override
    public Timer timer(String name) {
        return REGISTRY.timer(name);
    }

    @Override
    public Timer timer(Object source, String name) {
        return timer(createMetricName(source, "timer", name));
    }

    @Override
    public Meter meter(String name) {
        return REGISTRY.meter(name);
    }

    @Override
    public Meter meter(Object source, String name) {
        return meter(createMetricName(source, "meter", name));
    }

    @Override
    public void removeMetric(String metricName) {
        REGISTRY.remove(metricName);
    }

    @Deprecated
    @Override
    public String getNamePrefix(Object obj) {
        return String.format("%s.%d.", obj.getClass().getName(), JmxMetricsManager.nextId());
    }

    @Override
    public String createMetricName(Object source, String metricType, String name) {
        Class rootClass = getRootClass(source.getClass());
        return String.format(
                "%s:type=%s,service=%s,name=%s,metricType=%s,id=%s",
                DOMAIN,
                rootClass.getSimpleName(),
                source.getClass().getSimpleName(),
                name,
                metricType,
                Integer.toString(nextId())
        );
    }

    private Class getRootClass(Class clazz) {
        Class superClass = clazz.getSuperclass();
        if (superClass == Object.class) {
            return clazz;
        }
        return getRootClass(superClass);
    }
}
