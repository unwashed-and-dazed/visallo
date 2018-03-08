package org.visallo.web.changeEmail;

import org.visallo.webster.Handler;
import org.visallo.webster.handlers.StaticResourceHandler;
import org.visallo.core.model.Description;
import org.visallo.core.model.Name;
import org.visallo.web.VisalloCsrfHandler;
import org.visallo.web.WebApp;
import org.visallo.web.WebAppPlugin;
import org.visallo.web.privilegeFilters.ReadPrivilegeFilter;

import javax.servlet.ServletContext;

@Name("Change E-Mail")
@Description("Allows a user to change their e-mail address")
public class ChangeEmailWebPlugin implements WebAppPlugin {
    @Override
    public void init(WebApp app, ServletContext servletContext, Handler authenticationHandler) {
        Class<? extends Handler> authenticationHandlerClass = authenticationHandler.getClass();
        Class<? extends Handler> csrfHandlerClass = VisalloCsrfHandler.class;

        app.registerJavaScript("/org/visallo/web/changeEmail/changeEmail.js");
        app.registerResourceBundle("/org/visallo/web/changeEmail/messages.properties");

        app.registerJavaScriptTemplate("/org/visallo/web/changeEmail/template.hbs");

        app.post("/changeEmail", authenticationHandlerClass, csrfHandlerClass, ReadPrivilegeFilter.class, ChangeEmail.class);
    }
}
