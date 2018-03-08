package org.visallo.core.security;

import java.util.List;

public class DirectVisibilityTranslator extends VisibilityTranslatorBase {
    protected void addSourceToRequiredVisibilities(List<String> required, String source) {
        if (source != null && source.trim().length() > 0) {
            required.add(source.trim());
        }
    }
}
