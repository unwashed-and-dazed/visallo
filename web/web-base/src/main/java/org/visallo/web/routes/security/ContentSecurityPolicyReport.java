package org.visallo.web.routes.security;

import com.google.common.base.Charsets;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.io.IOException;

public class ContentSecurityPolicyReport implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(ContentSecurityPolicyReport.class);

    @Handle
    public void reportViolation(HttpServletRequest request) {
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP
        try {
            String json = IOUtils.toString(request.getInputStream(), Charsets.UTF_8);
            JSONObject input = new JSONObject(json);
            JSONObject report = input.getJSONObject("csp-report");
            LOGGER.error(
                    "Content-Security-Policy violation: '%s' Violated rule: '%s'",
                    report.getString("blocked-uri"),
                    report.getString("violated-directive")
            );
        } catch (JSONException jse) {
            throw new VisalloException("Unable to process Content-Security-Policy report", jse);
        } catch (IOException e) {
            throw new VisalloException("Unable to process Content-Security-Policy report", e);
        }

    }
}
