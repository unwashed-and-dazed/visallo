package org.visallo.core.status;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;

public interface MetricsManager {
    @Deprecated
    String getNamePrefix(Object obj);

    Counter counter(String metricName);

    Counter counter(Object source, String name);

    Timer timer(String metricName);

    Timer timer(Object source, String name);

    Meter meter(String metricName);

    Meter meter(Object source, String name);

    void removeMetric(String metricName);

    String createMetricName(Object source, String type, String name);
}
