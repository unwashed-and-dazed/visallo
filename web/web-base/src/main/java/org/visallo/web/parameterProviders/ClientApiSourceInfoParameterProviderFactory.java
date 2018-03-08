package org.visallo.web.parameterProviders;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.core.config.Configuration;
import org.visallo.web.clientapi.model.ClientApiSourceInfo;
import org.visallo.webster.HandlerChain;
import org.visallo.webster.parameterProviders.ParameterProvider;
import org.visallo.webster.parameterProviders.ParameterProviderFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@Singleton
public class ClientApiSourceInfoParameterProviderFactory extends ParameterProviderFactory<ClientApiSourceInfo> {
    private ParameterProvider<ClientApiSourceInfo> parameterProvider;

    @Inject
    public ClientApiSourceInfoParameterProviderFactory(Configuration configuration) {
        parameterProvider = new VisalloBaseParameterProvider<ClientApiSourceInfo>(configuration) {
            @Override
            public ClientApiSourceInfo getParameter(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) {
                final String sourceInfoString = getOptionalParameter(request, "sourceInfo");
                return ClientApiSourceInfo.fromString(sourceInfoString);
            }
        };
    }

    @Override
    public boolean isHandled(Method handleMethod, Class<? extends ClientApiSourceInfo> parameterType, Annotation[] parameterAnnotations) {
        return ClientApiSourceInfo.class.isAssignableFrom(parameterType);
    }

    @Override
    public ParameterProvider<ClientApiSourceInfo> createParameterProvider(Method handleMethod, Class<?> parameterType, Annotation[] parameterAnnotations) {
        return parameterProvider;
    }
}
