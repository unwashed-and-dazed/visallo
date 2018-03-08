package org.visallo.core.http;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.core.config.Configuration;

@Singleton
public class DefaultHttpRepository extends HttpRepository {
    @Inject
    public DefaultHttpRepository(Configuration configuration) {
        super(configuration);
    }
}
