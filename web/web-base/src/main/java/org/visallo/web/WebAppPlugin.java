package org.visallo.web;

import org.visallo.webster.Handler;

import javax.servlet.ServletContext;

public interface WebAppPlugin {
    void init(WebApp app, ServletContext servletContext, Handler authenticationHandler);
}
