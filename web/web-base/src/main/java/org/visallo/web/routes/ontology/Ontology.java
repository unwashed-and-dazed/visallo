package org.visallo.web.routes.ontology;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.web.VisalloResponse;
import org.visallo.web.clientapi.model.ClientApiOntology;
import org.visallo.web.clientapi.util.ObjectMapperFactory;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

@Singleton
public class Ontology implements ParameterizedHandler {
    private final OntologyRepository ontologyRepository;

    @Inject
    public Ontology(final OntologyRepository ontologyRepository) {
        this.ontologyRepository = ontologyRepository;
    }

    @Handle
    public ClientApiOntology handle(
            @ActiveWorkspaceId String workspaceId,
            VisalloResponse response
    ) throws Exception {
        ClientApiOntology result = ontologyRepository.getClientApiObject(workspaceId);

        String json = ObjectMapperFactory.getInstance().writeValueAsString(result);

        String eTag = response.generateETag(json.getBytes());
        if (!response.testEtagHeaders(eTag)) {
            response.addETagHeader(eTag);
        }

        return result;
    }
}
