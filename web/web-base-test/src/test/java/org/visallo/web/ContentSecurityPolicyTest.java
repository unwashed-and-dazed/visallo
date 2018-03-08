package org.visallo.web;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.visallo.core.config.Configuration;
import org.visallo.core.config.HashMapConfigurationLoader;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.visallo.web.ContentSecurityPolicy.*;

@RunWith(MockitoJUnitRunner.class)
public class ContentSecurityPolicyTest {

    private static final String URL = "localhost:9999";
    private static final String URL_HTTPS = "https://" + URL;
    private static final String URL_WEBSOCKET = "wss://" + URL;

    private static final String DEFAULT_DEFAULT_SRC = "default-src 'self';";
    private static final String DEFAULT_SCRIPT_SRC = " script-src 'self' 'unsafe-inline' 'unsafe-eval' blob:;";
    private static final String DEFAULT_STYLE_SRC = " style-src 'self' 'unsafe-inline';";
    private static final String DEFAULT_IMG_SRC = " img-src * data: blob:;";
    private static final String DEFAULT_CONNECT_SRC = " connect-src 'self' " + URL_WEBSOCKET + ";";
    private static final String DEFAULT_FONT_SRC = " font-src 'self' data:;";
    private static final String DEFAULT_FRAME_ANCESTORS = " frame-ancestors 'none';";
    private static final String DEFAULT_FORM_ACTION = " form-action 'self';";
    private static final String DEFAULT_REPORT_URI = " report-uri /csp-report;";
    private static final String DEFAULT_EXPECTED = DEFAULT_DEFAULT_SRC +
        DEFAULT_SCRIPT_SRC +
        DEFAULT_STYLE_SRC +
        DEFAULT_IMG_SRC +
        DEFAULT_CONNECT_SRC +
        DEFAULT_FONT_SRC +
        DEFAULT_FRAME_ANCESTORS +
        DEFAULT_FORM_ACTION +
        DEFAULT_REPORT_URI;

    private ContentSecurityPolicy contentSecurityPolicy;
    private Map<String, String> config;

    @Mock
    private HttpServletRequest request;

    @Before
    public void before() {
        config = new HashMap<>();
    }

    @Test
    public void testCreatePolicyOverride() {
        String override = "default-src *;";
        config.put(CONTENT_SECURITY_POLICY, override);
        assertEquals(override, getPolicy());
    }

    @Test
    public void testCreatePolicyOverrideIgnoresOthers() {
        String override = "default-src *;";
        config.put(CONTENT_SECURITY_POLICY, override);
        config.put(PREFIX + DEFAULT_SRC, "ignore1");
        config.put(PREFIX + DEFAULT_SRC + APPEND, "ignore2");
        assertEquals(override, getPolicy());
    }

    @Test
    public void testCreatePolicyNoConfig() {
        assertEquals(DEFAULT_EXPECTED, getPolicy());
    }

    @Test
    public void testCreatePolicyReplacePart() {
        config.put(PREFIX + IMG_SRC, "'none'");
        assertEquals(
                DEFAULT_DEFAULT_SRC +
                DEFAULT_SCRIPT_SRC +
                DEFAULT_STYLE_SRC +
                " img-src 'none';" +
                DEFAULT_CONNECT_SRC +
                DEFAULT_FONT_SRC +
                DEFAULT_FRAME_ANCESTORS +
                DEFAULT_FORM_ACTION +
                DEFAULT_REPORT_URI,
                getPolicy());
    }

    @Test
    public void testCreatePolicyReplacePartWithUrl() {
        config.put(PREFIX + CONNECT_SRC, "'self' wss://{{url}}");
        assertEquals(
                DEFAULT_DEFAULT_SRC +
                DEFAULT_SCRIPT_SRC +
                DEFAULT_STYLE_SRC +
                DEFAULT_IMG_SRC +
                " connect-src 'self' wss://www.visallo.com;" +
                DEFAULT_FONT_SRC +
                DEFAULT_FRAME_ANCESTORS +
                DEFAULT_FORM_ACTION +
                DEFAULT_REPORT_URI,
                getPolicy("https://www.visallo.com"));
    }

    @Test
    public void testCreatePolicyAppendToAll() {
        config.put(PREFIX + DEFAULT_SRC + APPEND, "blah-DEFAULT_SRC");
        config.put(PREFIX + SCRIPT_SRC + APPEND, "blah-SCRIPT_SRC");
        config.put(PREFIX + STYLE_SRC + APPEND, "blah-STYLE_SRC");
        config.put(PREFIX + IMG_SRC + APPEND, "blah-IMG_SRC");
        config.put(PREFIX + CONNECT_SRC + APPEND, "blah-CONNECT_SRC");
        config.put(PREFIX + FONT_SRC + APPEND, "blah-FONT_SRC");
        config.put(PREFIX + FRAME_ANCESTORS + APPEND, "blah-FRAME_ANCESTORS");
        config.put(PREFIX + FORM_ACTION + APPEND, "blah-FORM_ACTION");
        config.put(PREFIX + OBJECT_SRC + APPEND, "blah-OBJECT_SRC");
        config.put(PREFIX + MEDIA_SRC + APPEND, "blah-MEDIA_SRC");
        config.put(PREFIX + FRAME_SRC + APPEND, "blah-FRAME_SRC");
        config.put(PREFIX + CHILD_SRC + APPEND, "blah-CHILD_SRC");
        config.put(PREFIX + SANDBOX + APPEND, "blah-SANDBOX");
        config.put(PREFIX + PLUGIN_TYPES + APPEND, "blah-PLUGIN_TYPES");

        assertEquals(
                "default-src 'self' blah-DEFAULT_SRC;" +
                        " script-src 'self' 'unsafe-inline' 'unsafe-eval' blob: blah-SCRIPT_SRC;" +
                        " style-src 'self' 'unsafe-inline' blah-STYLE_SRC;" +
                        " img-src * data: blob: blah-IMG_SRC;" +
                        " connect-src 'self' " + URL_WEBSOCKET + " blah-CONNECT_SRC;" +
                        " font-src 'self' data: blah-FONT_SRC;" +
                        " frame-ancestors 'none' blah-FRAME_ANCESTORS;" +
                        " form-action 'self' blah-FORM_ACTION;" +
                        " object-src blah-OBJECT_SRC;" +
                        " media-src blah-MEDIA_SRC;" +
                        " frame-src blah-FRAME_SRC;" +
                        " child-src blah-CHILD_SRC;" +
                        " plugin-types blah-PLUGIN_TYPES;" +
                        " sandbox blah-SANDBOX;" +
                        " report-uri /csp-report;",
                getPolicy());
    }

    @Test
    public void testCreatePolicyReplaceAll() {
        config.put(PREFIX + DEFAULT_SRC, "blah-DEFAULT_SRC");
        config.put(PREFIX + SCRIPT_SRC, "blah-SCRIPT_SRC");
        config.put(PREFIX + STYLE_SRC, "blah-STYLE_SRC");
        config.put(PREFIX + IMG_SRC, "blah-IMG_SRC");
        config.put(PREFIX + CONNECT_SRC, "blah-CONNECT_SRC");
        config.put(PREFIX + FONT_SRC, "blah-FONT_SRC");
        config.put(PREFIX + FRAME_ANCESTORS, "blah-FRAME_ANCESTORS");
        config.put(PREFIX + FORM_ACTION, "blah-FORM_ACTION");
        config.put(PREFIX + OBJECT_SRC, "blah-OBJECT_SRC");
        config.put(PREFIX + MEDIA_SRC, "blah-MEDIA_SRC");
        config.put(PREFIX + FRAME_SRC, "blah-FRAME_SRC");
        config.put(PREFIX + CHILD_SRC, "blah-CHILD_SRC");
        config.put(PREFIX + SANDBOX, "blah-SANDBOX");
        config.put(PREFIX + PLUGIN_TYPES, "blah-PLUGIN_TYPES");

        assertEquals(
                "default-src blah-DEFAULT_SRC;" +
                        " script-src blah-SCRIPT_SRC;" +
                        " style-src blah-STYLE_SRC;" +
                        " img-src blah-IMG_SRC;" +
                        " connect-src blah-CONNECT_SRC;" +
                        " font-src blah-FONT_SRC;" +
                        " frame-ancestors blah-FRAME_ANCESTORS;" +
                        " form-action blah-FORM_ACTION;" +
                        " object-src blah-OBJECT_SRC;" +
                        " media-src blah-MEDIA_SRC;" +
                        " frame-src blah-FRAME_SRC;" +
                        " child-src blah-CHILD_SRC;" +
                        " plugin-types blah-PLUGIN_TYPES;" +
                        " sandbox blah-SANDBOX;" +
                        " report-uri /csp-report;",
                getPolicy());
    }

    @Test
    public void testCreatePolicyReplacePartNullValue() {
        config.put(PREFIX + IMG_SRC, null);
        assertEquals(DEFAULT_EXPECTED, getPolicy());
    }

    @Test
    public void testCreatePolicyReplacePartPoorValueFormat() {
        config.put(PREFIX + IMG_SRC, "  'none'  ;");
        assertEquals(
                DEFAULT_DEFAULT_SRC +
                DEFAULT_SCRIPT_SRC +
                DEFAULT_STYLE_SRC +
                " img-src 'none';" +
                DEFAULT_CONNECT_SRC +
                DEFAULT_FONT_SRC +
                DEFAULT_FRAME_ANCESTORS +
                DEFAULT_FORM_ACTION +
                DEFAULT_REPORT_URI,
                getPolicy());
    }

    @Test
    public void testCreatePolicyAppendPart() {
        config.put(PREFIX + IMG_SRC + APPEND, "*.openstreetmap.com");
        assertEquals(
                DEFAULT_DEFAULT_SRC +
                DEFAULT_SCRIPT_SRC +
                DEFAULT_STYLE_SRC +
                " img-src * data: blob: *.openstreetmap.com;" +
                DEFAULT_CONNECT_SRC +
                DEFAULT_FONT_SRC +
                DEFAULT_FRAME_ANCESTORS +
                DEFAULT_FORM_ACTION +
                DEFAULT_REPORT_URI,
                getPolicy());
    }

    @Test
    public void testCreatePolicyAppendPartPoorValueFormat() {
        config.put(PREFIX + IMG_SRC + APPEND, " *.openstreetmap.com ; ");
        assertEquals(
                DEFAULT_DEFAULT_SRC +
                DEFAULT_SCRIPT_SRC +
                DEFAULT_STYLE_SRC +
                " img-src * data: blob: *.openstreetmap.com;" +
                DEFAULT_CONNECT_SRC +
                DEFAULT_FONT_SRC +
                DEFAULT_FRAME_ANCESTORS +
                DEFAULT_FORM_ACTION +
                DEFAULT_REPORT_URI,
                getPolicy());
    }


    @Test
    public void testCreatePolicyAppendPartNullValue() {
        config.put(PREFIX + IMG_SRC + APPEND, null);
        assertEquals(DEFAULT_EXPECTED, getPolicy());
    }

    private String getPolicy() {
        return getPolicy(URL_HTTPS);
    }

    private String getPolicy(String requestURL) {
        Configuration configuration = new Configuration(new HashMapConfigurationLoader(new HashMap()), config);
        ContentSecurityPolicy contentSecurityPolicy = new ContentSecurityPolicy(configuration);
        when(request.getRequestURL()).thenReturn(new StringBuffer(requestURL));
        return contentSecurityPolicy.generatePolicy(request);
    }
}
