package org.visallo.web.webEventListeners;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sun.management.ThreadMXBean;
import org.visallo.core.config.Configurable;
import org.visallo.core.config.Configuration;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.core.util.VisalloPlugin;
import org.visallo.web.WebApp;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.management.ManagementFactory;
import java.util.Enumeration;
import java.util.Map;

@VisalloPlugin(disabledByDefault = true)
@Singleton
public class MemoryLoggerWebEventListener extends DefaultWebEventListener {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(MemoryLoggerWebEventListener.class);
    private static final String BEFORE_MEMORY_ATTR_NAME = MemoryLoggerWebEventListener.class.getName() + "-beforeMemory";
    private ThreadMXBean threadMXBean;
    private Config config;

    private static class Config {
        @Configurable(defaultValue = "10000000")
        public long infoThreshold;

        @Configurable(defaultValue = "50000000")
        public long warningThreshold;
    }

    @Inject
    public MemoryLoggerWebEventListener(Configuration configuration) {
        config = configuration.setConfigurables(new Config(), MemoryLoggerWebEventListener.class.getName());
    }

    @Override
    public void before(WebApp app, HttpServletRequest request, HttpServletResponse response) {
        long mem = getThreadAllocatedBytes();
        request.setAttribute(BEFORE_MEMORY_ATTR_NAME, mem);
    }

    @Override
    public void after(WebApp app, HttpServletRequest request, HttpServletResponse response) {
        Long beforeMem = (Long) request.getAttribute(BEFORE_MEMORY_ATTR_NAME);
        if (beforeMem == null) {
            LOGGER.error("Could not find before memory attribute: %s", BEFORE_MEMORY_ATTR_NAME);
            return;
        }
        long afterMem = getThreadAllocatedBytes();
        long usedMem = afterMem - beforeMem;
        StringBuilder logMessage = new StringBuilder();
        logMessage.append(String.format(
                "HTTP Thread Memory %s %s %d",
                request.getMethod(),
                request.getRequestURI(),
                usedMem
        ));
        if (usedMem > config.infoThreshold || usedMem > config.warningThreshold) {
            addHttpHeadersToLogMessage(logMessage, request);
            addHttpParametersToLogMessage(logMessage, request);
        }

        if (usedMem > config.warningThreshold) {
            LOGGER.warn("%s", logMessage.toString());
        } else if (usedMem > config.infoThreshold) {
            LOGGER.info("%s", logMessage.toString());
        } else {
            LOGGER.debug("%s", logMessage.toString());
        }
    }

    private void addHttpHeadersToLogMessage(StringBuilder logMessage, HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            Enumeration<String> headerValues = request.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                String headerValue = headerValues.nextElement();
                logMessage.append("\n   HEADER: ").append(headerName).append(": ").append(headerValue);
            }
        }
    }

    private void addHttpParametersToLogMessage(StringBuilder logMessage, HttpServletRequest request) {
        for (Map.Entry<String, String[]> parameters : request.getParameterMap().entrySet()) {
            for (String value : parameters.getValue()) {
                logMessage.append("\n   PARAM: ").append(parameters.getKey()).append(": ").append(value);
            }
        }
    }

    private long getThreadAllocatedBytes() {
        return getThreadMXBean().getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    private ThreadMXBean getThreadMXBean() {
        if (threadMXBean == null) {
            threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        }
        return threadMXBean;
    }
}
