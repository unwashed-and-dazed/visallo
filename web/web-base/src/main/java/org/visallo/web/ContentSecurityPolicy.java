package org.visallo.web;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.core.config.Configuration;

import javax.servlet.http.HttpServletRequest;
import java.util.StringJoiner;

import static org.apache.commons.lang.StringUtils.trimToNull;

@Singleton
public class ContentSecurityPolicy {
    protected static final String CONTENT_SECURITY_POLICY = "web.response.header.Content-Security-Policy";
    protected static final String PREFIX = CONTENT_SECURITY_POLICY + ".";
    protected static final String APPEND = ".append";

    protected static final String DEFAULT_SRC = "default-src";
    protected static final String SCRIPT_SRC = "script-src";
    protected static final String STYLE_SRC = "style-src";
    protected static final String IMG_SRC = "img-src";
    protected static final String CONNECT_SRC = "connect-src";
    protected static final String FONT_SRC = "font-src";
    protected static final String OBJECT_SRC = "object-src";
    protected static final String MEDIA_SRC = "media-src";
    protected static final String FRAME_SRC = "frame-src";
    protected static final String CHILD_SRC = "child-src";
    protected static final String FRAME_ANCESTORS = "frame-ancestors";
    protected static final String FORM_ACTION = "form-action";
    protected static final String SANDBOX = "sandbox";
    protected static final String PLUGIN_TYPES = "plugin-types";
    protected static final String REPORT_URI = "report-uri";

    protected static final String SELF = "'self'";
    protected static final String UNSAFE_INLINE = "'unsafe-inline'";
    protected static final String UNSAFE_EVAL = "'unsafe-eval'";
    protected static final String ALL = "*";
    protected static final String NONE = "'none'";
    protected static final String DATA = "data:";
    protected static final String BLOB = "blob:";


    private final Configuration configuration;
    private String policyTemplate;

    @Inject
    public ContentSecurityPolicy(Configuration configuration) {
        this.configuration = configuration;
    }

    public String generatePolicy(HttpServletRequest request) {
        if (policyTemplate == null) {
            policyTemplate = configuration.get(CONTENT_SECURITY_POLICY, null);
            if (policyTemplate == null) {
                policyTemplate = buildPolicyTemplate();
            }
        }

        String url = request.getRequestURL().toString().replace("https://", "");
        return policyTemplate.replace("{{url}}", url);
    }

    private String buildPolicyTemplate() {
        StringBuilder sb = new StringBuilder();

        appendPart(sb, DEFAULT_SRC, SELF);
        appendPart(sb, SCRIPT_SRC, SELF, UNSAFE_INLINE, UNSAFE_EVAL, BLOB);
        appendPart(sb, STYLE_SRC, SELF, UNSAFE_INLINE);
        appendPart(sb, IMG_SRC, ALL, DATA, BLOB);

        // Need to specify websocket path since self implies same protocol and will block
        // websocket requests otherwise.
        appendPart(sb, CONNECT_SRC, SELF, "wss://{{url}}");

        appendPart(sb, FONT_SRC, SELF, DATA);
        appendPart(sb, FRAME_ANCESTORS, NONE);
        appendPart(sb, FORM_ACTION, SELF);
        appendPart(sb, OBJECT_SRC);
        appendPart(sb, MEDIA_SRC);
        appendPart(sb, FRAME_SRC);
        appendPart(sb, CHILD_SRC);
        appendPart(sb, PLUGIN_TYPES);
        appendPart(sb, SANDBOX);
        appendPart(sb, REPORT_URI, true, "/csp-report");

        return sb.toString();
    }

    private void appendPart(StringBuilder sb, String name, String... defaultValues) {
        appendPart(sb, name, false, defaultValues);
    }

    private void appendPart(StringBuilder sb, String name, boolean last, String... defaultValues) {
        String defaultValue = String.join(" ", defaultValues);
        StringJoiner values = new StringJoiner(" ");
        String value = trimNoSemicolon(configuration.get(PREFIX + name, defaultValue));
        if (value != null) {
            values.add(value);
        }
        String append = trimNoSemicolon(configuration.get(PREFIX + name + APPEND, null));
        if (append != null) {
            values.add(append);
        }

        if (values.length() > 0) {
            sb.append(name);
            sb.append(" ");
            sb.append(values);
            sb.append(";");

            if (!last) {
                sb.append(" ");
            }
        }
    }

    private String trimNoSemicolon(String str) {
        return trimToNull(str == null ? null : str.replaceAll("\\s*;\\s*$", ""));
    }
}
