package org.visallo.web.parameterProviders;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.core.config.Configuration;
import org.visallo.core.formula.FormulaEvaluator;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.webster.HandlerChain;
import org.visallo.webster.parameterProviders.ParameterProvider;
import org.visallo.webster.parameterProviders.ParameterProviderFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.ResourceBundle;

@Singleton
public class FormulaEvaluatorUserContextParameterProviderFactory extends ParameterProviderFactory<FormulaEvaluator.UserContext> {
    private final ParameterProvider<FormulaEvaluator.UserContext> parameterProvider;

    @Inject
    public FormulaEvaluatorUserContextParameterProviderFactory(
            Configuration configuration,
            WorkspaceRepository workspaceRepository
    ) {
        parameterProvider = new VisalloBaseParameterProvider<FormulaEvaluator.UserContext>(configuration) {
            @Override
            public FormulaEvaluator.UserContext getParameter(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) {
                Locale locale = getLocale(request);
                String timeZone = getTimeZone(request);
                ResourceBundle resourceBundle = getBundle(request);
                String workspaceId = getActiveWorkspaceIdOrDefault(request, workspaceRepository);
                return new FormulaEvaluator.UserContext(locale, resourceBundle, timeZone, workspaceId);
            }
        };
    }

    @Override
    public boolean isHandled(Method handleMethod, Class<? extends FormulaEvaluator.UserContext> parameterType, Annotation[] parameterAnnotations) {
        return FormulaEvaluator.UserContext.class.isAssignableFrom(parameterType);
    }

    @Override
    public ParameterProvider<FormulaEvaluator.UserContext> createParameterProvider(Method handleMethod, Class<?> parameterType, Annotation[] parameterAnnotations) {
        return parameterProvider;
    }
}
