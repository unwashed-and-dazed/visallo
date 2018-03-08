package org.visallo.web.parameterProviders;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.core.config.Configuration;
import org.visallo.core.user.User;
import org.visallo.web.CurrentUser;
import org.visallo.webster.HandlerChain;
import org.visallo.webster.parameterProviders.ParameterProvider;
import org.visallo.webster.parameterProviders.ParameterProviderFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@Singleton
public class UserParameterProviderFactory extends ParameterProviderFactory<User> {
    private final ParameterProvider<User> parameterProvider;

    @Inject
    public UserParameterProviderFactory(Configuration configuration) {
        parameterProvider = new VisalloBaseParameterProvider<User>(configuration) {
            @Override
            public User getParameter(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    HandlerChain chain
            ) {
                return CurrentUser.get(request);
            }
        };
    }

    @Override
    public boolean isHandled(
            Method handleMethod,
            Class<? extends User> parameterType,
            Annotation[] parameterAnnotations
    ) {
        return User.class.isAssignableFrom(parameterType);
    }

    @Override
    public ParameterProvider<User> createParameterProvider(
            Method handleMethod,
            Class<?> parameterType,
            Annotation[] parameterAnnotations
    ) {
        return parameterProvider;
    }
}
