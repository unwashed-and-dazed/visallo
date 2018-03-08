package org.visallo.web.parameterProviders;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.core.config.Configuration;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.webster.HandlerChain;
import org.visallo.webster.parameterProviders.ParameterProvider;
import org.visallo.webster.parameterProviders.ParameterProviderFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class ActiveWorkspaceIdParameterProviderFactory extends ParameterProviderFactory<String> {
    private final ParameterProvider<String> requiredParameterProvider;
    private final ParameterProvider<String> notRequiredParameterProvider;

    @Inject
    public ActiveWorkspaceIdParameterProviderFactory(
            Configuration configuration,
            WorkspaceRepository workspaceRepository) {
        requiredParameterProvider = new VisalloBaseParameterProvider<String>(configuration) {
            @Override
            public String getParameter(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) {
                return getActiveWorkspaceId(request, workspaceRepository);
            }
        };
        notRequiredParameterProvider = new VisalloBaseParameterProvider<String>(configuration) {
            @Override
            public String getParameter(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) {
                return getActiveWorkspaceIdOrDefault(request, workspaceRepository);
            }
        };
    }

    @Override
    public boolean isHandled(Method handleMethod, Class<? extends String> parameterType, Annotation[] parameterAnnotations) {
        return getActiveWorkspaceIdAnnotation(parameterAnnotations) != null;
    }

    @Override
    public ParameterProvider<String> createParameterProvider(Method handleMethod, Class<?> parameterType, Annotation[] parameterAnnotations) {
        ActiveWorkspaceId activeWorkspaceIdAnnotation = getActiveWorkspaceIdAnnotation(parameterAnnotations);
        checkNotNull(activeWorkspaceIdAnnotation, "cannot find " + ActiveWorkspaceId.class.getName());
        if (activeWorkspaceIdAnnotation.required()) {
            return requiredParameterProvider;
        } else {
            return notRequiredParameterProvider;
        }
    }

    private static ActiveWorkspaceId getActiveWorkspaceIdAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof ActiveWorkspaceId) {
                return (ActiveWorkspaceId) annotation;
            }
        }
        return null;
    }
}
