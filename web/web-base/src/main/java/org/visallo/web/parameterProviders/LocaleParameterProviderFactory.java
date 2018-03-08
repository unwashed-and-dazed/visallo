package org.visallo.web.parameterProviders;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.core.config.Configuration;
import org.visallo.webster.HandlerChain;
import org.visallo.webster.parameterProviders.ParameterProvider;
import org.visallo.webster.parameterProviders.ParameterProviderFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Locale;

@Singleton
public class LocaleParameterProviderFactory extends ParameterProviderFactory<Locale> {
    private final ParameterProvider<Locale> parameterProvider;

    @Inject
    public LocaleParameterProviderFactory(Configuration configuration) {
        parameterProvider = new VisalloBaseParameterProvider<Locale>(configuration) {
            @Override
            public Locale getParameter(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) {
                return getLocale(request);
            }
        };
    }

    @Override
    public boolean isHandled(Method handleMethod, Class<? extends Locale> parameterType, Annotation[] parameterAnnotations) {
        return Locale.class.isAssignableFrom(parameterType);
    }

    @Override
    public ParameterProvider<Locale> createParameterProvider(Method handleMethod, Class<?> parameterType, Annotation[] parameterAnnotations) {
        return parameterProvider;
    }
}
