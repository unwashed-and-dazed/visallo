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
import java.util.ResourceBundle;

@Singleton
public class WebAppParameterProviderFactory extends ParameterProviderFactory<WebApp> {
    private ParameterProvider<WebApp> parameterProvider;

    @Inject
    public WebAppParameterProviderFactory(Configuration configuration) {
        parameterProvider = new VisalloBaseParameterProvider<WebApp>(configuration) {
            @Override
            public WebApp getParameter(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) {
                return getWebApp(request);
            }
        };
    }

    @Override
    public boolean isHandled(Method handleMethod, Class<? extends WebApp> parameterType, Annotation[] parameterAnnotations) {
        return ResourceBundle.class.isAssignableFrom(parameterType);
    }

    @Override
    public ParameterProvider<WebApp> createParameterProvider(Method handleMethod, Class<?> parameterType, Annotation[] parameterAnnotations) {
        return parameterProvider;
    }
}
