package org.visallo.core.util;

import org.apache.commons.io.IOUtils;
import org.visallo.core.exception.VisalloException;

import java.io.IOException;
import java.io.InputStream;

public class ResourceUtil {
    public static byte[] getResourceAsByteArray(Class sourceClass, String resourceName) {
        try {
            InputStream in = sourceClass.getResourceAsStream(resourceName);
            if (in == null) {
                throw new IOException("Could not find resource: " + resourceName);
            }
            return IOUtils.toByteArray(in);
        } catch (IOException e) {
            throw new VisalloException("Could not load resource. " + sourceClass.getName() + " at " + resourceName, e);
        }
    }
}
