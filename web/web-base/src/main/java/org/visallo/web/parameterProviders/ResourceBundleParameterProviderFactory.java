package org.visallo.web.parameterProviders;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.core.config.Configuration;
import org.visallo.web.WebApp;
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
public class ResourceBundleParameterProviderFactory extends ParameterProviderFactory<ResourceBundle> {
    private ParameterProvider<ResourceBundle> parameterProvider;

    @Inject
    public ResourceBundleParameterProviderFactory(Configuration configuration) {
        parameterProvider = new VisalloBaseParameterProvider<ResourceBundle>(configuration) {
            @Override
            public ResourceBundle getParameter(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) {
                WebApp webApp = getWebApp(request);
                Locale locale = getLocale(request);
                return webApp.getBundle(locale);
            }
        };
    }

    @Override
    public boolean isHandled(Method handleMethod, Class<? extends ResourceBundle> parameterType, Annotation[] parameterAnnotations) {
        return ResourceBundle.class.isAssignableFrom(parameterType);
    }

    @Override
    public ParameterProvider<ResourceBundle> createParameterProvider(Method handleMethod, Class<?> parameterType, Annotation[] parameterAnnotations) {
        return parameterProvider;
    }
}
