package org.visallo.graphCheck.rules;

import com.google.inject.Inject;
import org.vertexium.Direction;
import org.vertexium.Vertex;
import org.visallo.core.config.Configurable;
import org.visallo.core.config.Configuration;
import org.visallo.graphCheck.DefaultGraphCheckRule;
import org.visallo.graphCheck.GraphCheckContext;

import static org.vertexium.util.IterableUtils.count;

public class HighlyConnectedGraphCheckRule extends DefaultGraphCheckRule {
    private static final String CONFIGURATION_PROPERTY_PREFIX = HighlyConnectedGraphCheckRule.class.getName();
    private final Config config;

    @Inject
    public HighlyConnectedGraphCheckRule(Configuration configuration) {
        config = configuration.setConfigurables(new Config(), CONFIGURATION_PROPERTY_PREFIX);
    }

    @Override
    public void visitVertex(GraphCheckContext ctx, Vertex vertex) {
        int edgeCount = count(vertex.getEdgeIds(Direction.BOTH, ctx.getAuthorizations()));
        if (edgeCount > config.errorLevel) {
            ctx.reportError(this, vertex, "Highly connected vertex (%d > %d)", edgeCount, config.errorLevel);
        } else if (edgeCount > config.warningLevel) {
            ctx.reportWarning(this, vertex, "Highly connected vertex (%d > %d)", edgeCount, config.warningLevel);
        }
    }

    private static class Config {
        @Configurable(defaultValue = "10000")
        public int errorLevel;

        @Configurable(defaultValue = "5000")
        public int warningLevel;
    }
}
