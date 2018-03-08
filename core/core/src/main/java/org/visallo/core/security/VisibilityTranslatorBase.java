package org.visallo.core.security;

import org.vertexium.Visibility;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class VisibilityTranslatorBase extends VisibilityTranslator {
    @Override
    public VisalloVisibility toVisibility(VisibilityJson visibilityJson) {
        return new VisalloVisibility(toVisibilityNoSuperUser(visibilityJson));
    }

    @Override
    public VisalloVisibility toVisibility(String visibilitySource) {
        return toVisibility(visibilitySourceToVisibilityJson(visibilitySource));
    }

    protected VisibilityJson visibilitySourceToVisibilityJson(String visibilitySource) {
        return new VisibilityJson(visibilitySource);
    }

    @Override
    public Visibility toVisibilityNoSuperUser(VisibilityJson visibilityJson) {
        StringBuilder visibilityString = new StringBuilder();

        List<String> required = new ArrayList<>();

        String source = visibilityJson.getSource();
        addSourceToRequiredVisibilities(required, source);

        Set<String> workspaces = visibilityJson.getWorkspaces();
        if (workspaces != null) {
            required.addAll(workspaces);
        }

        for (String v : required) {
            if (visibilityString.length() > 0) {
                visibilityString.append("&");
            }
            visibilityString
                    .append("(")
                    .append(v)
                    .append(")");
        }
        return new Visibility(visibilityString.toString());
    }

    protected abstract void addSourceToRequiredVisibilities(List<String> required, String source);

    @Override
    public Visibility getDefaultVisibility() {
        return new Visibility("");
    }
}
