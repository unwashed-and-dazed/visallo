package org.visallo.graphCheck;

import com.beust.jcommander.Parameters;
import com.google.inject.Inject;
import org.vertexium.*;
import org.visallo.core.cmdline.CommandLineTool;
import org.visallo.core.model.user.GraphAuthorizationRepository;

import java.util.List;

@Parameters(commandDescription = "Checks the graph for common errors")
public class GraphCheck extends CommandLineTool {
    private GraphAuthorizationRepository graphAuthorizationRepository;
    private Authorizations authorizations;

    public static void main(String[] args) throws Exception {
        CommandLineTool.main(new GraphCheck(), args);
    }

    @Override
    protected int run() throws Exception {
        GraphCheckContext ctx = new GraphCheckContext(getAuthorizations());
        GraphCheckVertexiumObjectVisitor visitor = new GraphCheckVertexiumObjectVisitor(ctx, getConfiguration());

        Iterable<Vertex> vertices = getGraph().getVertices(FetchHint.ALL_INCLUDING_HIDDEN, getAuthorizations());
        getGraph().visit(vertices, visitor);

        Iterable<Edge> edges = getGraph().getEdges(FetchHint.ALL_INCLUDING_HIDDEN, getAuthorizations());
        getGraph().visit(edges, visitor);

        return 0;
    }

    @Override
    protected Authorizations getAuthorizations() {
        if (authorizations == null) {
            List<String> graphAuths = graphAuthorizationRepository.getGraphAuthorizations();
            String[] additionalAuths = graphAuths.toArray(new String[graphAuths.size()]);
            authorizations = getAuthorizationRepository().getGraphAuthorizations(getUser(), additionalAuths);
        }
        return authorizations;
    }

    @Inject
    public void setGraphAuthorizationRepository(GraphAuthorizationRepository graphAuthorizationRepository) {
        this.graphAuthorizationRepository = graphAuthorizationRepository;
    }
}
