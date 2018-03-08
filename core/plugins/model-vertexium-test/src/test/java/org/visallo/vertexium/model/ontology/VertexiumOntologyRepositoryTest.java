package org.visallo.vertexium.model.ontology;

import org.vertexium.Authorizations;
import org.visallo.core.config.Configuration;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.ontology.Concept;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.model.ontology.OntologyRepositoryTestBase;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.user.PrivilegeRepository;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.user.SystemUser;

import static org.visallo.core.model.user.UserRepository.USER_CONCEPT_IRI;

public class VertexiumOntologyRepositoryTest extends OntologyRepositoryTestBase {
    private VertexiumOntologyRepository ontologyRepository;

    @Override
    protected OntologyRepository getOntologyRepository() {
        if (ontologyRepository != null) {
            return ontologyRepository;
        }
        try {
            ontologyRepository = new VertexiumOntologyRepository(
                    getGraph(),
                    getGraphRepository(),
                    getVisibilityTranslator(),
                    getConfiguration(),
                    getGraphAuthorizationRepository(),
                    getLockRepository(),
                    getCacheService()
            ) {
                @Override
                public void loadOntologies(Configuration config, Authorizations authorizations) throws Exception {
                    SystemUser systemUser = new SystemUser();
                    Concept rootConcept = getOrCreateConcept(null, ROOT_CONCEPT_IRI, "root", null, systemUser, PUBLIC);
                    getOrCreateConcept(rootConcept, ENTITY_CONCEPT_IRI, "thing", null, systemUser, PUBLIC);
                    getOrCreateConcept(null, USER_CONCEPT_IRI, "visalloUser", null, false, systemUser, PUBLIC);
                    defineRequiredProperties(getGraph());
                    clearCache();
                }

                @Override
                protected PrivilegeRepository getPrivilegeRepository() {
                    return VertexiumOntologyRepositoryTest.this.getPrivilegeRepository();
                }

                @Override
                protected WorkspaceRepository getWorkspaceRepository() {
                    return VertexiumOntologyRepositoryTest.this.getWorkspaceRepository();
                }
            };
        } catch (Exception ex) {
            throw new VisalloException("Could not create ontology repository", ex);
        }
        return ontologyRepository;
    }
}

