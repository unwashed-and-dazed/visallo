package org.visallo.core.security;

import com.google.inject.Inject;
import org.vertexium.*;
import org.visallo.core.exception.VisalloAccessDeniedException;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.ontology.*;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.user.PrivilegeRepository;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.clientapi.model.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.visallo.core.model.properties.VisalloProperties.CONCEPT_TYPE_THING;
import static org.visallo.core.model.user.PrivilegeRepository.hasPrivilege;

public abstract class ACLProvider {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(ACLProvider.class);
    protected final Graph graph;
    protected final UserRepository userRepository;
    protected final OntologyRepository ontologyRepository;
    private final PrivilegeRepository privilegeRepository;

    @Inject
    protected ACLProvider(
            Graph graph,
            UserRepository userRepository,
            OntologyRepository ontologyRepository,
            PrivilegeRepository privilegeRepository
    ) {
        this.graph = graph;
        this.userRepository = userRepository;
        this.ontologyRepository = ontologyRepository;
        this.privilegeRepository = privilegeRepository;
    }

    public boolean canDeleteElement(Element element, User user, String workspaceId) {
        OntologyElement ontologyElement = getOntologyElement(element, workspaceId);
        return canDeleteElement(element, ontologyElement, null, user, workspaceId);
    }

    protected abstract boolean canDeleteElement(Element element, OntologyElement ontologyElement, Ontology ontology, User user, String workspaceId);

    public boolean canDeleteElement(ClientApiElement clientApiElement, User user, String workspaceId) {
        OntologyElement ontologyElement = getOntologyElement(clientApiElement, workspaceId);
        return canDeleteElement(clientApiElement, ontologyElement, null, user, workspaceId);
    }

    protected abstract boolean canDeleteElement(ClientApiElement clientApiElement, OntologyElement ontologyElement, Ontology ontology, User user, String workspaceId);

    public boolean canDeleteProperty(Element element, String propertyKey, String propertyName, User user, String workspaceId) {
        OntologyElement ontologyElement = getOntologyElement(element, workspaceId);
        return canDeleteProperty(element, ontologyElement, propertyKey, propertyName, null, user, workspaceId);
    }

    protected abstract boolean canDeleteProperty(Element element, OntologyElement ontologyElement, String propertyKey, String propertyName, Ontology ontology, User user, String workspaceId);

    public boolean canDeleteProperty(ClientApiElement clientApiElement, String propertyKey, String propertyName, User user, String workspaceId) {
        OntologyElement ontologyElement = getOntologyElement(clientApiElement, workspaceId);
        return canDeleteProperty(clientApiElement, ontologyElement, propertyKey, propertyName, null, user, workspaceId);
    }

    protected abstract boolean canDeleteProperty(ClientApiElement clientApiElement, OntologyElement ontologyElement, String propertyKey, String propertyName, Ontology ontology, User user, String workspaceId);

    public boolean canUpdateElement(Element element, User user, String workspaceId) {
        OntologyElement ontologyElement = getOntologyElement(element, workspaceId);
        return canUpdateElement(element, ontologyElement, null, user, workspaceId);
    }

    protected abstract boolean canUpdateElement(Element element, OntologyElement ontologyElement, Ontology ontology, User user, String workspaceId);

    public boolean canUpdateElement(ClientApiElement clientApiElement, User user, String workspaceId) {
        OntologyElement ontologyElement = getOntologyElement(clientApiElement, workspaceId);
        return canUpdateElement(clientApiElement, ontologyElement, null, user, workspaceId);
    }

    protected abstract boolean canUpdateElement(ClientApiElement clientApiElement, OntologyElement ontologyElement, Ontology ontology, User user, String workspaceId);

    public boolean canUpdateProperty(Element element, String propertyKey, String propertyName, User user, String workspaceId) {
        OntologyElement ontologyElement = getOntologyElement(element, workspaceId);
        return canUpdateProperty(element, ontologyElement, propertyKey, propertyName, null, user, workspaceId);
    }

    protected abstract boolean canUpdateProperty(Element element, OntologyElement ontologyElement, String propertyKey, String propertyName, Ontology ontology, User user, String workspaceId);

    public boolean canUpdateProperty(ClientApiElement clientApiElement, String propertyKey, String propertyName, User user, String workspaceId) {
        OntologyElement ontologyElement = getOntologyElement(clientApiElement, workspaceId);
        return canUpdateProperty(clientApiElement, ontologyElement, propertyKey, propertyName, null, user, workspaceId);
    }

    protected abstract boolean canUpdateProperty(ClientApiElement clientApiElement, OntologyElement ontologyElement, String propertyKey, String propertyName, Ontology ontology, User user, String workspaceId);

    public boolean canAddProperty(Element element, String propertyKey, String propertyName, User user, String workspaceId) {
        OntologyElement ontologyElement = getOntologyElement(element, workspaceId);
        return canAddProperty(element, ontologyElement, propertyKey, propertyName, null, user, workspaceId);
    }

    protected abstract boolean canAddProperty(Element element, OntologyElement ontologyElement, String propertyKey, String propertyName, Ontology ontology, User user, String workspaceId);

    public boolean canAddProperty(ClientApiElement clientApiElement, String propertyKey, String propertyName, User user, String workspaceId) {
        OntologyElement ontologyElement = getOntologyElement(clientApiElement, workspaceId);
        return canAddProperty(clientApiElement, ontologyElement, propertyKey, propertyName, null, user, workspaceId);
    }

    protected abstract boolean canAddProperty(ClientApiElement clientApiElement, OntologyElement ontologyElement, String propertyKey, String propertyName, Ontology ontology, User user, String workspaceId);

    public final void checkCanAddOrUpdateProperty(Element element, String propertyKey, String propertyName, User user, String workspaceId) {
        Set<String> privileges = privilegeRepository.getPrivileges(user);
        OntologyElement ontologyElement = getOntologyElement(element, workspaceId);
        checkCanAddOrUpdateProperty(element, ontologyElement, propertyKey, propertyName, null, privileges, user, workspaceId);
    }

    private void checkCanAddOrUpdateProperty(
            Element element,
            OntologyElement ontologyElement,
            String propertyKey,
            String propertyName,
            Ontology ontology,
            Set<String> privileges,
            User user,
            String workspaceId
    ) throws VisalloAccessDeniedException {
        boolean isUpdate = element.getProperty(propertyKey, propertyName) != null;
        boolean canAddOrUpdate = isUpdate
                ? internalCanUpdateProperty(element, ontologyElement, propertyKey, propertyName, ontology, privileges, user, workspaceId)
                : internalCanAddProperty(element, ontologyElement, propertyKey, propertyName, ontology, privileges, user, workspaceId);

        if (!canAddOrUpdate) {
            throw new VisalloAccessDeniedException(
                    propertyName + " cannot be added or updated due to ACL restriction", user, element.getId());
        }
    }

    public final void checkCanAddOrUpdateProperty(
            ClientApiElement clientApiElement,
            String propertyKey,
            String propertyName,
            User user,
            String workspaceId) {
        OntologyElement ontologyElement = getOntologyElement(clientApiElement, workspaceId);
        checkCanAddOrUpdateProperty(clientApiElement, ontologyElement, null, propertyKey, propertyName, user, workspaceId);
    }

    private void checkCanAddOrUpdateProperty(
            ClientApiElement clientApiElement,
            OntologyElement ontologyElement,
            Ontology ontology,
            String propertyKey,
            String propertyName,
            User user,
            String workspaceId
    ) throws VisalloAccessDeniedException {
        Set<String> privileges = privilegeRepository.getPrivileges(user);
        boolean isUpdate = clientApiElement.getProperty(propertyKey, propertyName) != null;
        boolean canAddOrUpdate = isUpdate
                ? internalCanUpdateProperty(clientApiElement, ontologyElement, propertyKey, propertyName, ontology, privileges, user, workspaceId)
                : internalCanAddProperty(clientApiElement, ontologyElement, propertyKey, propertyName, ontology, privileges, user, workspaceId);

        if (!canAddOrUpdate) {
            throw new VisalloAccessDeniedException(
                    propertyName + " cannot be added or updated due to ACL restriction", user, clientApiElement.getId());
        }
    }

    public final void checkCanDeleteProperty(Element element, String propertyKey, String propertyName, User user, String workspaceId) {
        Set<String> privileges = privilegeRepository.getPrivileges(user);
        OntologyElement ontologyElement = getOntologyElement(element, workspaceId);
        checkCanDeleteProperty(element, ontologyElement, propertyKey, propertyName, null, privileges, user, workspaceId);
    }

    private void checkCanDeleteProperty(
            Element element,
            OntologyElement ontologyElement,
            String propertyKey,
            String propertyName,
            Ontology ontology,
            Set<String> privileges,
            User user,
            String workspaceId
    ) throws VisalloAccessDeniedException {
        boolean canDelete = internalCanDeleteProperty(element, ontologyElement, propertyKey, propertyName, ontology, privileges, user, workspaceId);
        if (!canDelete) {
            throw new VisalloAccessDeniedException(propertyName + " cannot be deleted due to ACL restriction", user, element.getId());
        }
    }

    public final void checkCanDeleteProperty(ClientApiElement clientApiElement, String propertyKey, String propertyName, User user, String workspaceId) {
        Set<String> privileges = privilegeRepository.getPrivileges(user);
        OntologyElement ontologyElement = getOntologyElement(clientApiElement, workspaceId);
        checkCanDeleteProperty(clientApiElement, ontologyElement, propertyKey, propertyName, null, privileges, user, workspaceId);
    }

    private void checkCanDeleteProperty(
            ClientApiElement clientApiElement,
            OntologyElement ontologyElement,
            String propertyKey,
            String propertyName,
            Ontology ontology,
            Set<String> privileges,
            User user,
            String workspaceId
    ) throws VisalloAccessDeniedException {
        boolean canDelete = internalCanDeleteProperty(clientApiElement, ontologyElement, propertyKey, propertyName, ontology, privileges, user, workspaceId);

        if (!canDelete) {
            throw new VisalloAccessDeniedException(
                    propertyName + " cannot be deleted due to ACL restriction", user, clientApiElement.getId());
        }
    }

    public final ClientApiElementAcl elementACL(ClientApiElement clientApiElement, User user, String workspaceId) {
        Set<String> privileges = privilegeRepository.getPrivileges(user);
        OntologyElement ontologyElement = getOntologyElement(clientApiElement, workspaceId);
        return elementACL(clientApiElement, ontologyElement, null, privileges, user, workspaceId);
    }

    private ClientApiElementAcl elementACL(
            ClientApiElement clientApiElement,
            OntologyElement ontologyElement,
            Ontology ontology,
            Set<String> privileges,
            User user,
            String workspaceId
    ) {
        checkNotNull(clientApiElement, "clientApiElement is required");
        ClientApiElementAcl elementAcl = new ClientApiElementAcl();
        elementAcl.setAddable(true);
        elementAcl.setUpdateable(internalCanUpdateElement(clientApiElement, ontologyElement, ontology, privileges, user, workspaceId));
        elementAcl.setDeleteable(internalCanDeleteElement(clientApiElement, ontologyElement, ontology, privileges, user, workspaceId));

        List<ClientApiPropertyAcl> propertyAcls = elementAcl.getPropertyAcls();
        if (clientApiElement instanceof ClientApiVertex) {
            String iri = VisalloProperties.CONCEPT_TYPE.getPropertyValue(clientApiElement);
            while (iri != null) {
                Concept concept = ontology == null ? ontologyRepository.getConceptByIRI(iri, workspaceId) : ontology.getConceptByIri(iri);
                if (concept == null) {
                    LOGGER.warn("Could not find concept: %s", iri);
                    break;
                }
                populatePropertyAcls(concept, clientApiElement, ontologyElement, ontology, privileges, user, workspaceId, propertyAcls);
                iri = concept.getParentConceptIRI();
            }
        } else if (clientApiElement instanceof ClientApiEdge) {
            String iri = ((ClientApiEdge) clientApiElement).getLabel();
            while (iri != null) {
                Relationship relationship = ontology == null ? ontologyRepository.getRelationshipByIRI(iri, workspaceId) : ontology.getRelationshipByIri(iri);
                if (relationship == null) {
                    LOGGER.warn("Could not find relationship: %s", iri);
                    break;
                }
                populatePropertyAcls(relationship, clientApiElement, ontologyElement, ontology, privileges, user, workspaceId, propertyAcls);
                iri = relationship.getParentIRI();
            }
        } else {
            throw new VisalloException("unsupported ClientApiElement class " + clientApiElement.getClass().getName());
        }
        return elementAcl;
    }

    public final ClientApiObject appendACL(ClientApiObject clientApiObject, User user, String workspaceId) {
        if (user == null) {
            return clientApiObject;
        }
        Set<String> privileges = privilegeRepository.getPrivileges(user);
        Ontology ontology = ontologyRepository.getOntology(workspaceId);
        return appendACL(clientApiObject, ontology, privileges, user, workspaceId);
    }

    private ClientApiObject appendACL(ClientApiObject clientApiObject, Ontology ontology, Set<String> privileges, User user, String workspaceId) {
        if (clientApiObject instanceof ClientApiElement) {
            appendACL((ClientApiElement) clientApiObject, ontology, privileges, user, workspaceId);
        } else if (clientApiObject instanceof ClientApiWorkspaceVertices) {
            appendACL(((ClientApiWorkspaceVertices) clientApiObject).getVertices(), ontology, user, workspaceId);
        } else if (clientApiObject instanceof ClientApiVertexMultipleResponse) {
            appendACL(((ClientApiVertexMultipleResponse) clientApiObject).getVertices(), ontology, user, workspaceId);
        } else if (clientApiObject instanceof ClientApiEdgeMultipleResponse) {
            appendACL(((ClientApiEdgeMultipleResponse) clientApiObject).getEdges(), ontology, user, workspaceId);
        } else if (clientApiObject instanceof ClientApiElementSearchResponse) {
            appendACL(((ClientApiElementSearchResponse) clientApiObject).getElements(), ontology, user, workspaceId);
            List<ClientApiVertexiumObject> referencedElements = ((ClientApiElementSearchResponse) clientApiObject).getReferencedElements();
            if (referencedElements != null) {
                appendACL(referencedElements, ontology, user, workspaceId);
            }
        } else if (clientApiObject instanceof ClientApiEdgeSearchResponse) {
            appendACL(((ClientApiEdgeSearchResponse) clientApiObject).getResults(), ontology, user, workspaceId);
        } else if (clientApiObject instanceof ClientApiVertexEdges) {
            ClientApiVertexEdges vertexEdges = (ClientApiVertexEdges) clientApiObject;
            appendACL(vertexEdges, ontology, privileges, user, workspaceId);
        } else if (clientApiObject instanceof ClientApiElementFindRelatedResponse) {
            appendACL(((ClientApiElementFindRelatedResponse) clientApiObject).getElements(), ontology, user, workspaceId);
        }

        return clientApiObject;
    }

    protected final boolean isComment(String propertyName) {
        return VisalloProperties.COMMENT.isSameName(propertyName);
    }

    protected final boolean isAuthor(Element element, String propertyKey, String propertyName, User user, String workspaceId) {
        if (element == null) {
            return false;
        }
        Property property = element.getProperty(propertyKey, propertyName);
        if (property != null) {
            String authorUserId = VisalloProperties.MODIFIED_BY_METADATA.getMetadataValue(property.getMetadata());
            return user.getUserId().equals(authorUserId);
        } else {
            return false;
        }
    }

    protected final boolean isAuthor(ClientApiElement clientApiElement, String propertyKey, String propertyName, User user, String workspaceId) {
        if (clientApiElement == null) {
            return false;
        }
        ClientApiProperty property = clientApiElement.getProperty(propertyKey, propertyName);
        if (property != null) {
            String authorUserId = VisalloProperties.MODIFIED_BY_METADATA.getMetadataValue(property.getMetadata());
            return user.getUserId().equals(authorUserId);
        } else {
            return false;
        }
    }

    protected final boolean isLongText(OntologyProperty ontologyProperty) {
        if (ontologyProperty != null) {
            String displayType = ontologyProperty.getDisplayType();
            return displayType != null && displayType.equals("longText");
        }
        return false;
    }

    private void appendACL(Collection<? extends ClientApiObject> clientApiObject, Ontology ontology, User user, String workspaceId) {
        Set<String> privileges = privilegeRepository.getPrivileges(user);
        for (ClientApiObject apiObject : clientApiObject) {
            appendACL(apiObject, ontology, privileges, user, workspaceId);
        }
    }

    private void appendACL(ClientApiElement clientApiElement, Ontology ontology, Set<String> privileges, User user, String workspaceId) {
        OntologyElement ontologyElement = getOntologyElement(clientApiElement, ontology, workspaceId);

        appendACL(clientApiElement, ontologyElement, ontology, privileges, user, workspaceId);
    }

    private void appendACL(
            ClientApiElement clientApiElement,
            OntologyElement ontologyElement,
            Ontology ontology,
            Set<String> privileges,
            User user,
            String workspaceId
    ) {
        for (ClientApiProperty apiProperty : clientApiElement.getProperties()) {
            String key = apiProperty.getKey();
            String name = apiProperty.getName();
            apiProperty.setUpdateable(internalCanUpdateProperty(clientApiElement, ontologyElement, key, name, ontology, privileges, user, workspaceId));
            apiProperty.setDeleteable(internalCanDeleteProperty(clientApiElement, ontologyElement, key, name, ontology, privileges, user, workspaceId));
            apiProperty.setAddable(internalCanAddProperty(clientApiElement, ontologyElement, key, name, ontology, privileges, user, workspaceId));
        }
        clientApiElement.setUpdateable(internalCanUpdateElement(clientApiElement, ontologyElement, ontology, privileges, user, workspaceId));
        clientApiElement.setDeleteable(internalCanDeleteElement(clientApiElement, ontologyElement, ontology, privileges, user, workspaceId));

        clientApiElement.setAcl(elementACL(clientApiElement, ontologyElement, ontology, privileges, user, workspaceId));

        if (clientApiElement instanceof ClientApiEdgeWithVertexData) {
            appendACL(((ClientApiEdgeWithVertexData) clientApiElement).getSource(), ontology, privileges, user, workspaceId);
            appendACL(((ClientApiEdgeWithVertexData) clientApiElement).getTarget(), ontology, privileges, user, workspaceId);
        }
    }

    private void appendACL(ClientApiVertexEdges edges, Ontology ontology, Set<String> privileges, User user, String workspaceId) {
        for (ClientApiVertexEdges.Edge vertexEdge : edges.getRelationships()) {
            appendACL(vertexEdge.getRelationship(), ontology, privileges, user, workspaceId);
            appendACL(vertexEdge.getVertex(), ontology, privileges, user, workspaceId);
        }
    }

    private void populatePropertyAcls(
            HasOntologyProperties hasOntologyProperties,
            ClientApiElement clientApiElement,
            OntologyElement ontologyElement,
            Ontology ontology,
            Set<String> privileges,
            User user,
            String workspaceId,
            List<ClientApiPropertyAcl> propertyAcls
    ) {
        Collection<OntologyProperty> ontologyProperties = hasOntologyProperties.getProperties();
        Set<String> addedPropertyNames = new HashSet<>();
        for (OntologyProperty ontologyProperty : ontologyProperties) {
            String propertyName = ontologyProperty.getTitle();
            for (ClientApiProperty property : clientApiElement.getProperties(propertyName)) {
                ClientApiPropertyAcl acl = newClientApiPropertyAcl(
                        clientApiElement,
                        ontologyElement,
                        property.getKey(),
                        propertyName,
                        ontology,
                        privileges,
                        user,
                        workspaceId
                );
                ClientApiPropertyAcl defaultAcl = newClientApiPropertyAcl(
                        null,
                        ontologyElement,
                        property.getKey(),
                        propertyName,
                        ontology,
                        privileges,
                        user,
                        workspaceId
                );
                if (!acl.equals(defaultAcl)) {
                    propertyAcls.add(acl);
                }
                addedPropertyNames.add(propertyName);
            }
        }

        // for properties that don't exist on the clientApiElement, use the ontology property definition and omit the key.
        propertyAcls.addAll(
                ontologyProperties.stream()
                        .filter(ontologyProperty -> !addedPropertyNames.contains(ontologyProperty.getTitle()))
                        .map(ontologyProperty -> {
                            String propertyName = ontologyProperty.getTitle();
                            ClientApiPropertyAcl acl = newClientApiPropertyAcl(
                                    clientApiElement,
                                    ontologyElement,
                                    null,
                                    propertyName,
                                    ontology,
                                    privileges,
                                    user,
                                    workspaceId
                            );
                            ClientApiPropertyAcl defaultAcl = newClientApiPropertyAcl(
                                    null,
                                    ontologyElement,
                                    null,
                                    propertyName,
                                    ontology,
                                    privileges,
                                    user,
                                    workspaceId
                            );
                            return acl.equals(defaultAcl) ? null : acl;
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList())
        );
    }

    private ClientApiPropertyAcl newClientApiPropertyAcl(
            ClientApiElement clientApiElement,
            OntologyElement ontologyElement,
            String key,
            String name,
            Ontology ontology,
            Set<String> privileges,
            User user,
            String workspaceId
    ) {
        ClientApiPropertyAcl propertyAcl = new ClientApiPropertyAcl();
        propertyAcl.setKey(key);
        propertyAcl.setName(name);
        propertyAcl.setAddable(internalCanAddProperty(clientApiElement, ontologyElement, key, name, ontology, privileges, user, workspaceId));
        propertyAcl.setUpdateable(internalCanUpdateProperty(clientApiElement, ontologyElement, key, name, ontology, privileges, user, workspaceId));
        propertyAcl.setDeleteable(internalCanDeleteProperty(clientApiElement, ontologyElement, key, name, ontology, privileges, user, workspaceId));
        return propertyAcl;
    }

    private boolean internalCanDeleteElement(ClientApiElement clientApiElement, OntologyElement ontologyElement, Ontology ontology, Set<String> privileges, User user, String workspaceId) {
        return hasPrivilege(privileges, Privilege.EDIT) && canDeleteElement(clientApiElement, ontologyElement, ontology, user, workspaceId);
    }

    private boolean internalCanUpdateElement(ClientApiElement clientApiElement, OntologyElement ontologyElement, Ontology ontology, Set<String> privileges, User user, String workspaceId) {
        return hasPrivilege(privileges, Privilege.EDIT) && canUpdateElement(clientApiElement, ontologyElement, ontology, user, workspaceId);
    }

    private boolean internalCanDeleteProperty(
            Element element,
            OntologyElement ontologyElement,
            String propertyKey,
            String propertyName,
            Ontology ontology,
            Set<String> privileges,
            User user,
            String workspaceId
    ) {
        boolean canDelete = hasEditOrCommentPrivilege(privileges, propertyName)
                && canDeleteProperty(element, ontologyElement, propertyKey, propertyName, ontology, user, workspaceId);
        if (canDelete && isComment(propertyName)) {
            canDelete = hasPrivilege(privileges, Privilege.COMMENT_DELETE_ANY) ||
                    (hasPrivilege(privileges, Privilege.COMMENT) && isAuthor(element, propertyKey, propertyName, user, workspaceId));
        }
        return canDelete;
    }

    private boolean internalCanDeleteProperty(
            ClientApiElement clientApiElement,
            OntologyElement ontologyElement,
            String propertyKey,
            String propertyName,
            Ontology ontology,
            Set<String> privileges,
            User user,
            String workspaceId
    ) {
        boolean canDelete = hasEditOrCommentPrivilege(privileges, propertyName)
                && canDeleteProperty(clientApiElement, ontologyElement, propertyKey, propertyName, ontology, user, workspaceId);
        if (canDelete && isComment(propertyName)) {
            canDelete = hasPrivilege(privileges, Privilege.COMMENT_DELETE_ANY) ||
                    (hasPrivilege(privileges, Privilege.COMMENT) && isAuthor(clientApiElement, propertyKey, propertyName, user, workspaceId));
        }
        return canDelete;
    }

    private boolean internalCanUpdateProperty(
            Element element,
            OntologyElement ontologyElement,
            String propertyKey,
            String propertyName,
            Ontology ontology,
            Set<String> privileges,
            User user,
            String workspaceId
    ) {
        boolean canUpdate = hasEditOrCommentPrivilege(privileges, propertyName)
                && canUpdateProperty(element, ontologyElement, propertyKey, propertyName, ontology, user, workspaceId);
        if (canUpdate && isComment(propertyName)) {
            canUpdate = hasPrivilege(privileges, Privilege.COMMENT_EDIT_ANY) ||
                    (hasPrivilege(privileges, Privilege.COMMENT) && isAuthor(element, propertyKey, propertyName, user, workspaceId));
        }
        return canUpdate;
    }

    private boolean internalCanUpdateProperty(
            ClientApiElement clientApiElement,
            OntologyElement ontologyElement,
            String propertyKey,
            String propertyName,
            Ontology ontology,
            Set<String> privileges,
            User user,
            String workspaceId
    ) {
        boolean canUpdate = hasEditOrCommentPrivilege(privileges, propertyName)
                && canUpdateProperty(clientApiElement, ontologyElement, propertyKey, propertyName, ontology, user, workspaceId);
        if (canUpdate && isComment(propertyName)) {
            canUpdate = hasPrivilege(privileges, Privilege.COMMENT_EDIT_ANY) ||
                    (hasPrivilege(privileges, Privilege.COMMENT) && isAuthor(clientApiElement, propertyKey, propertyName, user, workspaceId));
        }
        return canUpdate;
    }

    private boolean internalCanAddProperty(
            Element element,
            OntologyElement ontologyElement,
            String propertyKey,
            String propertyName,
            Ontology ontology,
            Set<String> privileges,
            User user,
            String workspaceId
    ) {
        boolean canAdd = hasEditOrCommentPrivilege(privileges, propertyName)
                && canAddProperty(element, ontologyElement, propertyKey, propertyName, ontology, user, workspaceId);
        if (canAdd && isComment(propertyName)) {
            canAdd = hasPrivilege(privileges, Privilege.COMMENT);
        }
        return canAdd;
    }

    private boolean internalCanAddProperty(
            ClientApiElement clientApiElement,
            OntologyElement ontologyElement,
            String propertyKey,
            String propertyName,
            Ontology ontology,
            Set<String> privileges,
            User user,
            String workspaceId
    ) {
        boolean canAdd = hasEditOrCommentPrivilege(privileges, propertyName)
                && canAddProperty(clientApiElement, ontologyElement, propertyKey, propertyName, ontology, user, workspaceId);
        if (canAdd && isComment(propertyName)) {
            canAdd = hasPrivilege(privileges, Privilege.COMMENT);
        }
        return canAdd;
    }

    private boolean hasEditOrCommentPrivilege(Set<String> privileges, String propertyName) {
        return hasPrivilege(privileges, Privilege.EDIT) || (isComment(propertyName) && hasPrivilege(privileges, Privilege.COMMENT));
    }

    protected OntologyElement getOntologyElement(Element element, String workspaceId) {
        if (element == null) {
            return null;
        }
        if (element instanceof Edge) {
            return getOntologyRelationshipFromElement((Edge) element, null, workspaceId);
        }
        if (element instanceof Vertex) {
            return getOntologyConceptFromElement((Vertex) element, null, workspaceId);
        }
        throw new VisalloException("Unexpected " + Element.class.getName() + " found " + element.getClass().getName());
    }

    protected OntologyElement getOntologyElement(ClientApiElement clientApiElement, String workspaceId) {
        return getOntologyElement(clientApiElement, null, workspaceId);
    }

    protected OntologyElement getOntologyElement(ClientApiElement clientApiElement, Ontology ontology, String workspaceId) {
        if (clientApiElement == null) {
            return null;
        }
        if (clientApiElement instanceof ClientApiEdge) {
            return getOntologyRelationshipFromElement((ClientApiEdge) clientApiElement, ontology, workspaceId);
        }
        if (clientApiElement instanceof ClientApiVertex) {
            return getOntologyConceptFromElement((ClientApiVertex) clientApiElement, ontology, workspaceId);
        }
        throw new VisalloException("Unexpected " + ClientApiVertex.class.getName() + " found " + clientApiElement.getClass().getName());
    }

    private Relationship getOntologyRelationshipFromElement(Edge e, Ontology ontology, String workspaceId) {
        String label = e.getLabel();
        return getOntologyRelationshipFromElement(label, ontology, workspaceId);
    }

    private Relationship getOntologyRelationshipFromElement(ClientApiEdge e, Ontology ontology, String workspaceId) {
        String label = e.getLabel();
        return getOntologyRelationshipFromElement(label, ontology, workspaceId);
    }

    private Relationship getOntologyRelationshipFromElement(String edgeLabel, Ontology ontology, String workspaceId) {
        checkNotNull(edgeLabel, "Edge label cannot be null");
        Relationship relationship = ontology == null ? ontologyRepository.getRelationshipByIRI(edgeLabel, workspaceId) : ontology.getRelationshipByIri(edgeLabel);
        checkNotNull(relationship, edgeLabel + " does not exist in ontology");
        return relationship;
    }

    private Concept getOntologyConceptFromElement(Vertex vertex, Ontology ontology, String workspaceId) {
        String iri = VisalloProperties.CONCEPT_TYPE.getPropertyValue(vertex, CONCEPT_TYPE_THING);
        return getOntologyConcept(iri, ontology, workspaceId);
    }

    private Concept getOntologyConceptFromElement(ClientApiVertex vertex, Ontology ontology, String workspaceId) {
        String iri = VisalloProperties.CONCEPT_TYPE.getPropertyValue(vertex, CONCEPT_TYPE_THING);
        return getOntologyConcept(iri, ontology, workspaceId);
    }

    private Concept getOntologyConcept(String conceptType, Ontology ontology, String workspaceId) {
        if (conceptType == null) {
            return null;
        }
        return ontology == null ? ontologyRepository.getConceptByIRI(conceptType, workspaceId) : ontology.getConceptByIri(conceptType);
    }
}
