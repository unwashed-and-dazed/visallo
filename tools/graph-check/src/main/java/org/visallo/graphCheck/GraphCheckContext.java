package org.visallo.graphCheck;

import org.vertexium.*;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;

public class GraphCheckContext {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(GraphCheckContext.class);
    private final Authorizations authorizations;

    public GraphCheckContext(Authorizations authorizations) {
        this.authorizations = authorizations;
    }

    public Authorizations getAuthorizations() {
        return authorizations;
    }

    public void reportError(GraphCheckRule rule, VertexiumObject object, String messageFormat, Object... messageParams) {
        Object[] params = getMessageParams(rule, object, messageParams);
        LOGGER.error("%s: " + messageFormat, params);
    }

    public void reportWarning(GraphCheckRule rule, VertexiumObject object, String messageFormat, Object... messageParams) {
        Object[] params = getMessageParams(rule, object, messageParams);
        LOGGER.warn("%s: " + messageFormat, params);
    }

    public void reportError(GraphCheckRule rule, VertexiumObject object, Property property, String messageFormat, Object... messageParams) {
        Object[] params = getMessageParams(rule, object, property, messageParams);
        LOGGER.error("%s: " + messageFormat, params);
    }

    public void reportWarning(GraphCheckRule rule, VertexiumObject object, Property property, String messageFormat, Object... messageParams) {
        Object[] params = getMessageParams(rule, object, property, messageParams);
        LOGGER.warn("%s: " + messageFormat, params);
    }

    private Object[] getMessageParams(GraphCheckRule rule, VertexiumObject object, Object... messageParams) {
        String prefix = String.format(
                "%s:%s:%s: ",
                rule.getClass().getSimpleName(),
                getVertexiumObjectTypeAsString(object),
                object.getId()
        );
        Object[] params = new Object[1 + messageParams.length];
        params[0] = prefix;
        System.arraycopy(messageParams, 0, params, 1, messageParams.length);
        return params;
    }

    private Object[] getMessageParams(GraphCheckRule rule, VertexiumObject object, Property property, Object... messageParams) {
        String prefix = String.format(
                "%s:%s:%s:%s[%s]: ",
                rule.getClass().getSimpleName(),
                getVertexiumObjectTypeAsString(object),
                object.getId(),
                property.getName(),
                property.getKey()
        );
        Object[] params = new Object[1 + messageParams.length];
        params[0] = prefix;
        System.arraycopy(messageParams, 0, params, 1, messageParams.length);
        return params;
    }

    private Object getVertexiumObjectTypeAsString(VertexiumObject object) {
        if (object instanceof Vertex) {
            return "vertex";
        } else if (object instanceof Edge) {
            return "edge";
        } else if (object instanceof ExtendedDataRow) {
            return "extdatarow";
        } else {
            throw new VisalloException("Unhandled VertexiumObject type: " + object.getClass().getName());
        }
    }
}
