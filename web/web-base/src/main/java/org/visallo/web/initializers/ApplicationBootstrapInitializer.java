package org.visallo.web.initializers;

import javax.servlet.ServletContext;

/**
 * @deprecated Implement {@link org.visallo.core.process.VisalloProcess} instead.
 */
@Deprecated
public abstract class ApplicationBootstrapInitializer {
    public abstract void initialize(ServletContext context);
}
