package org.visallo.web.webEventListeners;

import com.google.common.base.Joiner;
import org.visallo.core.trace.Trace;
import org.visallo.core.trace.TraceSpan;
import org.visallo.web.WebApp;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

public class TraceWebEventListener extends DefaultWebEventListener {
    private static final String TRACE_ATTRIBUTE = "org.visallo.web.webEventListeners.TraceWebEventListener.trace";
    private static final String GRAPH_TRACE_ENABLE = "graphTraceEnable";
    public static final int PRIORITY = CurrentUserWebEventListener.PRIORITY + 100;

    @Override
    public void before(WebApp app, HttpServletRequest request, HttpServletResponse response) {
        if (isGraphTraceEnabled(request)) {
            String traceDescription = request.getRequestURI();
            Map<String, String> parameters = new HashMap<>();
            for (Map.Entry<String, String[]> reqParameters : request.getParameterMap().entrySet()) {
                parameters.put(reqParameters.getKey(), Joiner.on(", ").join(reqParameters.getValue()));
            }
            TraceSpan trace = Trace.on(traceDescription, parameters);
            request.setAttribute(TRACE_ATTRIBUTE, trace);
        }
    }

    @Override
    public void always(WebApp app, HttpServletRequest request, HttpServletResponse response) {
        TraceSpan trace = (TraceSpan) request.getAttribute(TRACE_ATTRIBUTE);
        if (trace != null) {
            trace.close();
        }
        Trace.off();
    }

    private boolean isGraphTraceEnabled(ServletRequest req) {
        return req.getParameter(GRAPH_TRACE_ENABLE) != null || req instanceof HttpServletRequest && ((HttpServletRequest) req).getHeader(GRAPH_TRACE_ENABLE) != null;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
