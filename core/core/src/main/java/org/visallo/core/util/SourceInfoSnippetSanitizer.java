package org.visallo.core.util;

import org.apache.commons.lang3.StringEscapeUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceInfoSnippetSanitizer {
    private static Pattern snippetPattern = Pattern.compile(
            "^(.*?)<span class=\"selection\">(.+?)</span>(.*)$",
            Pattern.DOTALL
    );

    /**
     * Sanitize Snippet HTML parts:
     * 1. Before selection span
     * 2. Inside selection
     * 3. After selection
     *
     * If pattern doesn't match expected escape whole string
     *
     * @param snippet
     * @return sanitized snippet
     */
    public static String sanitizeSnippet(String snippet) {
        if (snippet == null) {
            return null;
        }

        Matcher matcher = snippetPattern.matcher(snippet);
        if (matcher.matches() && matcher.groupCount() == 3) {
            String prefix = matcher.group(1);
            String selection = matcher.group(2);
            String suffix = matcher.group(3);

            return escapeParts(prefix, selection, suffix);
        } else {
            return StringEscapeUtils.escapeXml11(snippet);
        }
    }

    private static String escapeParts(String prefix, String selection, String suffix) {
        StringBuilder str = new StringBuilder();

        str.append(StringEscapeUtils.escapeXml11(prefix));
        str.append("<span class=\"selection\">");
        str.append(StringEscapeUtils.escapeXml11(selection));
        str.append("</span>");
        str.append(StringEscapeUtils.escapeXml11(suffix));

        return str.toString();
    }
}
