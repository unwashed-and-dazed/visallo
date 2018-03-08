package org.visallo.vertexium.model.ontology;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.io.ReaderDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.vertexium.*;
import org.vertexium.mutation.ExistingElementMutation;
import org.vertexium.property.StreamingPropertyValue;
import org.vertexium.query.Contains;
import org.vertexium.query.QueryResultsIterable;
import org.vertexium.util.CloseableUtils;
import org.vertexium.util.ConvertingIterable;
import org.vertexium.util.IterableUtils;
import org.vertexium.util.StreamUtils;
import org.visallo.core.cache.CacheService;
import org.visallo.core.config.Configuration;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.graph.GraphRepository;
import org.visallo.core.model.graph.GraphUpdateContext;
import org.visallo.core.model.lock.LockRepository;
import org.visallo.core.model.ontology.*;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.user.GraphAuthorizationRepository;
import org.visallo.core.model.workQueue.Priority;
import org.visallo.core.model.workspace.WorkspaceProperties;
import org.visallo.core.security.VisalloVisibility;
import org.visallo.core.security.VisibilityTranslator;
import org.visallo.core.user.User;
import org.visallo.core.util.JSONUtil;
import org.visallo.core.util.OWLOntologyUtil;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.clientapi.model.ClientApiOntology;
import org.visallo.web.clientapi.model.PropertyType;
import org.visallo.web.clientapi.model.SandboxStatus;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class VertexiumOntologyRepository extends OntologyRepositoryBase {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(VertexiumOntologyRepository.class);
    public static final String ID_PREFIX = "ontology_";
    public static final String ID_PREFIX_PROPERTY = ID_PREFIX + "prop_";
    public static final String ID_PREFIX_RELATIONSHIP = ID_PREFIX + "rel_";
    public static final String ID_PREFIX_CONCEPT = ID_PREFIX + "concept_";
    private final Graph graph;
    private final GraphRepository graphRepository;
    private final VisibilityTranslator visibilityTranslator;

    private Authorizations publicOntologyAuthorizations;

    @Inject
    public VertexiumOntologyRepository(
            Graph graph,
            GraphRepository graphRepository,
            VisibilityTranslator visibilityTranslator,
            Configuration config,
            GraphAuthorizationRepository graphAuthorizationRepository,
            LockRepository lockRepository,
            CacheService cacheService
    ) throws Exception {
        super(config, lockRepository, cacheService);
        try {
            this.graph = graph;
            this.graphRepository = graphRepository;
            this.visibilityTranslator = visibilityTranslator;

            graphAuthorizationRepository.addAuthorizationToGraph(VISIBILITY_STRING);

            defineRequiredProperties(graph);

            publicOntologyAuthorizations = graph.createAuthorizations(Collections.singleton(VISIBILITY_STRING));

            loadOntologies(config, publicOntologyAuthorizations);
        } catch (Exception ex) {
            LOGGER.error("Could not initialize: %s", this.getClass().getName(), ex);
            throw ex;
        }
    }

    @Override
    protected void importOntologyAnnotationProperty(OWLOntology o, OWLAnnotationProperty annotationProperty, File inDir, Authorizations authorizations) {
        super.importOntologyAnnotationProperty(o, annotationProperty, inDir, authorizations);
        String about = annotationProperty.getIRI().toString();
        LOGGER.debug("disabling index for annotation property: " + about);
        if (!graph.isPropertyDefined(about)) {
            graph.defineProperty(about).dataType(String.class)
                    .textIndexHint(TextIndexHint.NONE)
                    .define();
        }
    }

    @Override
    public ClientApiOntology getClientApiObject(String workspaceId) {
        return super.getClientApiObject(workspaceId);
    }

    @Override
    public void clearCache() {
        LOGGER.info("clearing ontology cache");
        super.clearCache();
        graph.flush();
    }

    @Override
    public void clearCache(String workspaceId) {
        checkNotNull(workspaceId, "Workspace should not be null");
        LOGGER.info("clearing ontology cache for workspace %s", workspaceId);
        super.clearCache(workspaceId);
        graph.flush();
    }

    @Override
    protected void addEntityGlyphIconToEntityConcept(Concept entityConcept, byte[] rawImg, Authorizations authorizations) {
        StreamingPropertyValue raw = new StreamingPropertyValue(new ByteArrayInputStream(rawImg), byte[].class);
        raw.searchIndex(false);
        entityConcept.setProperty(OntologyProperties.GLYPH_ICON.getPropertyName(), raw, getSystemUser(), authorizations);
        graph.flush();
    }

    @Override
    public void storeOntologyFile(InputStream in, IRI documentIRI, Authorizations authorizations) {
        byte[] data;
        try {
            data = IOUtils.toByteArray(in);
        } catch (IOException ex) {
            throw new VisalloException("Could not read ontology input stream", ex);
        }
        String md5 = DigestUtils.md5Hex(data);
        StreamingPropertyValue value = new StreamingPropertyValue(new ByteArrayInputStream(data), byte[].class);
        value.searchIndex(false);
        Vertex rootConceptVertex = ((VertexiumConcept) getRootConcept(PUBLIC)).getVertex();
        Property existingProperty = OntologyProperties.ONTOLOGY_FILE.getProperty(rootConceptVertex, documentIRI.toString());
        Date modifiedDate = new Date();
        Metadata metadata;
        if (existingProperty == null) {
            metadata = getMetadata(modifiedDate, getSystemUser(), VISIBILITY.getVisibility());
            metadata.add("index", Iterables.size(OntologyProperties.ONTOLOGY_FILE.getProperties(rootConceptVertex)), VISIBILITY.getVisibility());
        } else {
            metadata = existingProperty.getMetadata();
        }

        VisibilityJson visibilityJson = new VisibilityJson(VISIBILITY.getVisibility().getVisibilityString());
        try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(Priority.LOW, getSystemUser(), getAuthorizations(PUBLIC))) {
            ctx.setPushOnQueue(false);
            ctx.update(rootConceptVertex, modifiedDate, visibilityJson, rootConceptCtx -> {
                OntologyProperties.ONTOLOGY_FILE.updateProperty(rootConceptCtx, documentIRI.toString(), value, metadata, VISIBILITY.getVisibility());
                OntologyProperties.ONTOLOGY_FILE_MD5.updateProperty(rootConceptCtx, documentIRI.toString(), md5, metadata, VISIBILITY.getVisibility());
            });
        }
    }

    @Override
    protected boolean hasFileChanged(IRI documentIRI, byte[] inFileData) {
        Vertex rootConceptVertex = ((VertexiumConcept) getRootConcept(PUBLIC)).getVertex();
        String existingMd5 = OntologyProperties.ONTOLOGY_FILE_MD5.getPropertyValue(rootConceptVertex, documentIRI.toString());
        return existingMd5 == null || !DigestUtils.md5Hex(inFileData).equals(existingMd5);
    }

    @Deprecated
    @Override
    public boolean isOntologyDefined(String iri) {
        Vertex rootConceptVertex = ((VertexiumConcept) getRootConcept(PUBLIC)).getVertex();
        Property prop = OntologyProperties.ONTOLOGY_FILE.getProperty(rootConceptVertex, iri);
        return prop != null;
    }

    @Override
    public List<OWLOntology> loadOntologyFiles(OWLOntologyManager m, OWLOntologyLoaderConfiguration config, IRI excludedIRI) throws OWLOntologyCreationException, IOException {
        List<OWLOntology> loadedOntologies = new ArrayList<>();
        Iterable<Property> ontologyFiles = getOntologyFiles();
        for (Property ontologyFile : ontologyFiles) {
            IRI ontologyFileIRI = IRI.create(ontologyFile.getKey());
            if (excludedIRI != null && excludedIRI.equals(ontologyFileIRI)) {
                // If we're skipping an ontology, we shouldn't be loading the rest since they may include it
                return loadedOntologies;
            }
            try (InputStream visalloBaseOntologyIn = ((StreamingPropertyValue) ontologyFile.getValue()).getInputStream()) {
                Reader visalloBaseOntologyReader = new InputStreamReader(visalloBaseOntologyIn);
                LOGGER.info("Loading existing ontology: %s", ontologyFile.getKey());
                OWLOntologyDocumentSource visalloBaseOntologySource = new ReaderDocumentSource(visalloBaseOntologyReader, ontologyFileIRI);
                try {
                    OWLOntology o = m.loadOntologyFromOntologyDocument(visalloBaseOntologySource, config);
                    loadedOntologies.add(o);
                } catch (UnloadableImportException ex) {
                    LOGGER.warn("Could not load existing %s", ontologyFileIRI, ex);
                }
            }
        }
        return loadedOntologies;
    }

    private Iterable<Property> getOntologyFiles() {
        VertexiumConcept rootConcept = (VertexiumConcept) getRootConcept(PUBLIC);
        checkNotNull(rootConcept, "Could not get root concept");
        Vertex rootConceptVertex = rootConcept.getVertex();
        checkNotNull(rootConceptVertex, "Could not get root concept vertex");

        List<Property> ontologyFiles = Lists.newArrayList(OntologyProperties.ONTOLOGY_FILE.getProperties(rootConceptVertex));
        ontologyFiles.sort((ontologyFile1, ontologyFile2) -> {
            Integer index1 = (Integer) ontologyFile1.getMetadata().getValue("index");
            checkNotNull(index1, "Could not find metadata (1) 'index' on " + ontologyFile1);
            Integer index2 = (Integer) ontologyFile2.getMetadata().getValue("index");
            checkNotNull(index2, "Could not find metadata (2) 'index' on " + ontologyFile2);
            return index1.compareTo(index2);
        });
        return ontologyFiles;
    }

    @Override
    public Iterable<Relationship> getRelationships(Iterable<String> ids, String workspaceId) {
        Iterable<Vertex> vertices = graph.getVertices(ids, getAuthorizations(workspaceId));
        return transformRelationships(vertices, workspaceId);
    }

    @Override
    public Iterable<Relationship> getRelationships(String workspaceId) {
        Iterable<Vertex> vertices = graph.getVerticesWithPrefix(ID_PREFIX_RELATIONSHIP, getAuthorizations(workspaceId));
        return transformRelationships(vertices, workspaceId);
    }

    private Relationship toVertexiumRelationship(String parentIRI, Vertex relationshipVertex, List<OntologyProperty> properties, Map<String, String> relatedVertexIdToIriMap, String workspaceId) {
        Authorizations authorizations = getAuthorizations(workspaceId);

        Set<String> domainVertexIds = IterableUtils.toSet(relationshipVertex.getVertexIds(Direction.IN, LabelName.HAS_EDGE.toString(), authorizations));
        List<String> domainIris = domainVertexIds.stream().map(relatedVertexIdToIriMap::get).collect(Collectors.toList());

        Set<String> rangeVertexIds = IterableUtils.toSet(relationshipVertex.getVertexIds(Direction.OUT, LabelName.HAS_EDGE.toString(), authorizations));
        List<String> rangeIris = rangeVertexIds.stream().map(relatedVertexIdToIriMap::get).collect(Collectors.toList());

        Set<String> inverseOfVertexIds = IterableUtils.toSet(relationshipVertex.getVertexIds(Direction.OUT, LabelName.INVERSE_OF.toString(), getAuthorizations(workspaceId)));
        List<String> inverseOfIRIs = inverseOfVertexIds.stream().map(relatedVertexIdToIriMap::get).collect(Collectors.toList());

        return createRelationship(parentIRI, relationshipVertex, inverseOfIRIs, domainIris, rangeIris, properties, workspaceId);
    }

    @Override
    public String getDisplayNameForLabel(String relationshipIRI, String workspaceId) {
        String displayName = null;
        if (relationshipIRI != null && !relationshipIRI.trim().isEmpty()) {
            try {
                Relationship relationship = getRelationshipByIRI(relationshipIRI, workspaceId);
                if (relationship != null) {
                    displayName = relationship.getDisplayName();
                }
            } catch (IllegalArgumentException iae) {
                throw new IllegalStateException(
                        String.format("Found multiple vertices for relationship label \"%s\"", relationshipIRI),
                        iae
                );
            }
        }
        return displayName;
    }

    @Override
    public Iterable<OntologyProperty> getProperties(Iterable<String> ids, String workspaceId) {
        Iterable<Vertex> vertices = graph.getVertices(ids, getAuthorizations(workspaceId));
        return transformProperties(vertices, workspaceId);
    }

    @Override
    public Iterable<OntologyProperty> getProperties(String workspaceId) {
        Iterable<Vertex> vertices = graph.getVerticesWithPrefix(ID_PREFIX_PROPERTY, getAuthorizations(workspaceId));
        return transformProperties(vertices, workspaceId);
    }

    protected ImmutableList<String> getDependentPropertyIris(Vertex vertex, String workspaceId) {
        List<Edge> dependentProperties = Lists.newArrayList(vertex.getEdges(Direction.OUT, OntologyProperties.EDGE_LABEL_DEPENDENT_PROPERTY, getAuthorizations(workspaceId)));
        dependentProperties.sort((e1, e2) -> {
            Integer o1 = OntologyProperties.DEPENDENT_PROPERTY_ORDER_PROPERTY_NAME.getPropertyValue(e1, 0);
            Integer o2 = OntologyProperties.DEPENDENT_PROPERTY_ORDER_PROPERTY_NAME.getPropertyValue(e2, 0);
            return Integer.compare(o1, o2);
        });
        return ImmutableList.copyOf(dependentProperties.stream().map(e -> {
            String propertyId = e.getOtherVertexId(vertex.getId());
            return propertyId.substring(VertexiumOntologyRepository.ID_PREFIX_PROPERTY.length());
        }).collect(Collectors.toList()));
    }

    @Override
    public Iterable<Concept> getConceptsWithProperties(String workspaceId) {
        Authorizations authorizations = getAuthorizations(workspaceId);
        return transformConcepts(graph.getVerticesWithPrefix(ID_PREFIX_CONCEPT, authorizations), workspaceId);
    }

    @Override
    public Concept getRootConcept(String workspaceId) {
        return getConceptByIRI(VertexiumOntologyRepository.ROOT_CONCEPT_IRI, workspaceId);
    }

    @Override
    public Concept getEntityConcept(String workspaceId) {
        return getConceptByIRI(VertexiumOntologyRepository.ENTITY_CONCEPT_IRI, workspaceId);
    }

    @Override
    protected List<Concept> getChildConcepts(Concept concept, String workspaceId) {
        Vertex conceptVertex = ((VertexiumConcept) concept).getVertex();
        return toConcepts(conceptVertex.getVertices(Direction.IN, LabelName.IS_A.toString(), getAuthorizations(workspaceId)), workspaceId);
    }

    @Override
    protected List<Relationship> getChildRelationships(Relationship relationship, String workspaceId) {
        Vertex relationshipVertex = ((VertexiumRelationship) relationship).getVertex();
        return transformRelationships(relationshipVertex.getVertices(Direction.IN, LabelName.IS_A.toString(), getAuthorizations(workspaceId)), workspaceId);
    }

    @Override
    public Relationship getParentRelationship(Relationship relationship, String workspaceId) {
        Vertex parentVertex = getParentVertex(((VertexiumRelationship) relationship).getVertex(), workspaceId);
        if (parentVertex == null) {
            return null;
        }

        String parentIri = OntologyProperties.ONTOLOGY_TITLE.getPropertyValue(parentVertex);
        return getRelationshipByIRI(parentIri, workspaceId);
    }

    @Override
    public Concept getParentConcept(final Concept concept, String workspaceId) {
        Vertex parentConceptVertex = getParentVertex(((VertexiumConcept) concept).getVertex(), workspaceId);
        if (parentConceptVertex == null) {
            return null;
        }

        String parentIri = OntologyProperties.ONTOLOGY_TITLE.getPropertyValue(parentConceptVertex);
        return getConceptByIRI(parentIri, workspaceId);
    }

    private List<Concept> toConcepts(Iterable<Vertex> vertices, String workspaceId) {
        ArrayList<Concept> concepts = new ArrayList<>();
        for (Vertex vertex : vertices) {
            concepts.add(createConcept(vertex, workspaceId));
        }
        return concepts;
    }

    @Override
    public Iterable<Concept> getConcepts(Iterable<String> ids, String workspaceId) {
        return transformConcepts(graph.getVertices(ids, getAuthorizations(workspaceId)), workspaceId);
    }

    @Override
    public Iterable<Concept> getConceptsByIRI(List<String> conceptIRIs, String workspaceId) {
        QueryResultsIterable<Vertex> vertices = getGraph().query(getAuthorizations(workspaceId))
                .has(VisalloProperties.CONCEPT_TYPE.getPropertyName(), OntologyRepository.TYPE_CONCEPT)
                .has(OntologyProperties.ONTOLOGY_TITLE.getPropertyName(), Contains.IN, conceptIRIs)
                .vertices();
        return transformConcepts(vertices, workspaceId);
    }

    @Override
    public Iterable<OntologyProperty> getPropertiesByIRI(List<String> propertyIRIs, String workspaceId) {
        QueryResultsIterable<Vertex> vertices = getGraph().query(getAuthorizations(workspaceId))
                .has(VisalloProperties.CONCEPT_TYPE.getPropertyName(), OntologyRepository.TYPE_PROPERTY)
                .has(OntologyProperties.ONTOLOGY_TITLE.getPropertyName(), Contains.IN, propertyIRIs)
                .vertices();
        return transformProperties(vertices, workspaceId);
    }

    @Override
    public Iterable<Relationship> getRelationshipsByIRI(List<String> relationshipIRIs, String workspaceId) {
        QueryResultsIterable<Vertex> vertices = getGraph().query(getAuthorizations(workspaceId))
                .has(VisalloProperties.CONCEPT_TYPE.getPropertyName(), OntologyRepository.TYPE_RELATIONSHIP)
                .has(OntologyProperties.ONTOLOGY_TITLE.getPropertyName(), Contains.IN, relationshipIRIs)
                .vertices();
        return transformRelationships(vertices, workspaceId);
    }

    @Override
    public List<OntologyProperty> getPropertiesByIntent(String intent, String workspaceId) {
        QueryResultsIterable<Vertex> vertices = getGraph().query(getAuthorizations(workspaceId))
                .has(VisalloProperties.CONCEPT_TYPE.getPropertyName(), OntologyRepository.TYPE_PROPERTY)
                .has(OntologyProperties.INTENT.getPropertyName(), intent)
                .vertices();
        return transformProperties(vertices, workspaceId);
    }

    @Override
    protected List<Concept> findLoadedConceptsByIntent(String intent, String workspaceId) {
        QueryResultsIterable<Vertex> vertices = getGraph().query(getAuthorizations(workspaceId))
                .has(VisalloProperties.CONCEPT_TYPE.getPropertyName(), OntologyRepository.TYPE_CONCEPT)
                .has(OntologyProperties.INTENT.getPropertyName(), intent)
                .vertices();
        return transformConcepts(vertices, workspaceId);
    }

    @Override
    protected List<Relationship> findLoadedRelationshipsByIntent(String intent, String workspaceId) {
        QueryResultsIterable<Vertex> vertices = getGraph().query(getAuthorizations(workspaceId))
                .has(VisalloProperties.CONCEPT_TYPE.getPropertyName(), OntologyRepository.TYPE_RELATIONSHIP)
                .has(OntologyProperties.INTENT.getPropertyName(), intent)
                .vertices();
        return transformRelationships(vertices, workspaceId);
    }

    private void internalDeleteObject(Vertex vertex, String workspaceId) {
        Authorizations authorizations = getAuthorizations(workspaceId);
        Iterable<EdgeInfo> edges = vertex.getEdgeInfos(Direction.BOTH, authorizations);
        for (EdgeInfo edge : edges) {
            graph.deleteEdge(edge.getEdgeId(), authorizations);
        }
        graph.deleteVertex(vertex.getId(), authorizations);
    }

    @Override
    protected void internalDeleteConcept(Concept concept, String workspaceId) {
        Vertex vertex = ((VertexiumConcept) concept).getVertex();
        internalDeleteObject(vertex, workspaceId);
    }

    @Override
    protected void internalDeleteProperty(OntologyProperty property, String workspaceId) {
        Vertex vertex = ((VertexiumOntologyProperty) property).getVertex();
        internalDeleteObject(vertex, workspaceId);
    }

    @Override
    protected void internalDeleteRelationship(Relationship relationship, String workspaceId) {
        Vertex vertex = ((VertexiumRelationship) relationship).getVertex();
        internalDeleteObject(vertex, workspaceId);
    }


    @Override
    protected Concept internalGetOrCreateConcept(Concept parent, String conceptIRI, String displayName, String glyphIconHref, String color, File inDir, boolean deleteChangeableProperties, User user, String workspaceId) {
        Concept concept = getConceptByIRI(conceptIRI, workspaceId);
        if (concept != null) {
            if (deleteChangeableProperties) {
                deleteChangeableProperties(concept, getAuthorizations(workspaceId));
            }
            return concept;
        }

        try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(getPriority(user), user, getAuthorizations(workspaceId))) {
            ctx.setPushOnQueue(false);

            Visibility visibility = VISIBILITY.getVisibility();
            VisibilityJson visibilityJson = new VisibilityJson(visibility.getVisibilityString());

            VertexBuilder builder = prepareVertex(ID_PREFIX_CONCEPT, conceptIRI, workspaceId, visibility, visibilityJson);

            Date modifiedDate = new Date();
            Vertex vertex = ctx.update(builder, modifiedDate, visibilityJson, TYPE_CONCEPT, elemCtx -> {
                Metadata metadata = getMetadata(modifiedDate, user, visibility);
                OntologyProperties.ONTOLOGY_TITLE.updateProperty(elemCtx, conceptIRI, metadata, visibility);
                OntologyProperties.DISPLAY_NAME.updateProperty(elemCtx, displayName, metadata, visibility);
                if (conceptIRI.equals(OntologyRepository.ENTITY_CONCEPT_IRI)) {
                    OntologyProperties.TITLE_FORMULA.updateProperty(elemCtx, "prop('http://visallo.org#title') || ('Untitled ' + ontology && ontology.displayName) ", metadata, visibility);
                    OntologyProperties.SUBTITLE_FORMULA.updateProperty(elemCtx, "(ontology && ontology.displayName) || prop('http://visallo.org#source') || ''", metadata, visibility);
                    OntologyProperties.TIME_FORMULA.updateProperty(elemCtx, "prop('http://visallo.org#modifiedDate') || ''", metadata, visibility);
                }
                if (!StringUtils.isEmpty(glyphIconHref)) {
                    OntologyProperties.GLYPH_ICON_FILE_NAME.updateProperty(elemCtx, glyphIconHref, metadata, visibility);
                }
                if (!StringUtils.isEmpty(color)) {
                    OntologyProperties.COLOR.updateProperty(elemCtx, color, metadata, visibility);
                }
            }).get();

            if (parent == null) {
                concept = createConcept(vertex, workspaceId);
            } else {
                concept = createConcept(vertex, null, parent.getIRI(), workspaceId);
                findOrAddEdge(ctx, ((VertexiumConcept) concept).getVertex(), ((VertexiumConcept) parent).getVertex(), LabelName.IS_A.toString());
            }

            if (!isPublic(workspaceId)) {
                findOrAddEdge(ctx, workspaceId, ((VertexiumConcept) concept).getVertex().getId(), WorkspaceProperties.WORKSPACE_TO_ONTOLOGY_RELATIONSHIP_IRI);
            }

            return concept;
        } catch (Exception e) {
            throw new VisalloException("Could not create concept: " + conceptIRI, e);
        }
    }

    private Metadata getMetadata(Date modifiedDate, User user, Visibility visibility) {
        Metadata metadata = new Metadata();
        VisalloProperties.MODIFIED_DATE_METADATA.setMetadata(metadata, modifiedDate, visibility);
        if (user != null) {
            VisalloProperties.MODIFIED_BY_METADATA.setMetadata(metadata, user.getUserId(), visibility);
        }
        return metadata;
    }

    private VertexBuilder prepareVertex(String prefix, String iri, String workspaceId, Visibility visibility, VisibilityJson visibilityJson) {

        if (isPublic(workspaceId)) {
            return graph.prepareVertex(prefix + iri, visibility);
        }

        String id = prefix + Hashing.sha1().hashString(workspaceId + iri, Charsets.UTF_8).toString();

        visibilityJson.addWorkspace(workspaceId);

        return graph.prepareVertex(id, visibilityTranslator.toVisibility(visibilityJson).getVisibility());
    }

    protected void findOrAddEdge(Vertex fromVertex, final Vertex toVertex, String edgeLabel, User user, String workspaceId) {
        try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(Priority.LOW, user, getAuthorizations(workspaceId))) {
            ctx.setPushOnQueue(false);
            findOrAddEdge(ctx, fromVertex, toVertex, edgeLabel);
        } catch (Exception e) {
            throw new VisalloException("Could not findOrAddEdge", e);
        }
    }


    protected void removeEdge(GraphUpdateContext ctx, String fromVertexId, String toVertexId) {
        String edgeId = fromVertexId + "-" + toVertexId;
        ctx.getGraph().deleteEdge(edgeId, ctx.getAuthorizations());
    }

    protected void findOrAddEdge(GraphUpdateContext ctx, String fromVertexId, String toVertexId, String edgeLabel) {
        String edgeId = fromVertexId + "-" + toVertexId;
        ctx.getOrCreateEdgeAndUpdate(edgeId, fromVertexId, toVertexId, edgeLabel, VISIBILITY.getVisibility(), elemCtx -> {
            if (elemCtx.isNewElement()) {
                VisibilityJson visibilityJson = new VisibilityJson(VISIBILITY.getVisibility().getVisibilityString());
                elemCtx.updateBuiltInProperties(new Date(), visibilityJson);
            }
        });
    }

    protected void findOrAddEdge(GraphUpdateContext ctx, Vertex fromVertex, Vertex toVertex, String edgeLabel) {
        findOrAddEdge(ctx, fromVertex.getId(), toVertex.getId(), edgeLabel);
    }

    @Override
    public void addDomainConceptsToRelationshipType(String relationshipIri, List<String> conceptIris, User user, String workspaceId) {
        checkPrivileges(user, workspaceId);
        try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(Priority.LOW, user, getAuthorizations(workspaceId))) {
            ctx.setPushOnQueue(false);
            VertexiumRelationship relationship = (VertexiumRelationship) getRelationshipByIRI(relationshipIri, workspaceId);
            Vertex relationshipVertex = relationship.getVertex();
            if (!isPublic(workspaceId) && relationship.getSandboxStatus() != SandboxStatus.PRIVATE) {
                throw new UnsupportedOperationException("Sandboxed updating of domain iris is not currently supported for published relationships");
            }

            Iterable<Concept> concepts = getConceptsByIRI(conceptIris, workspaceId);
            for (Concept concept : concepts) {
                checkNotNull(concept, "concepts cannot have null values");
                findOrAddEdge(ctx, ((VertexiumConcept) concept).getVertex(), relationshipVertex, LabelName.HAS_EDGE.toString());
            }
        }
    }

    @Override
    public void addRangeConceptsToRelationshipType(String relationshipIri, List<String> conceptIris, User user, String workspaceId) {
        checkPrivileges(user, workspaceId);
        try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(Priority.LOW, user, getAuthorizations(workspaceId))) {
            ctx.setPushOnQueue(false);
            VertexiumRelationship relationship = (VertexiumRelationship) getRelationshipByIRI(relationshipIri, workspaceId);
            Vertex relationshipVertex = relationship.getVertex();
            if (!isPublic(workspaceId) && relationship.getSandboxStatus() != SandboxStatus.PRIVATE) {
                throw new UnsupportedOperationException("Sandboxed updating of range iris is not currently supported for published relationships");
            }
            Iterable<Concept> concepts = getConceptsByIRI(conceptIris, workspaceId);
            for (Concept concept : concepts) {
                checkNotNull(concept, "concepts cannot have null values");
                findOrAddEdge(ctx, relationshipVertex, ((VertexiumConcept) concept).getVertex(), LabelName.HAS_EDGE.toString());
            }
        }
    }

    @Override
    protected OntologyProperty addPropertyTo(
            List<Concept> concepts,
            List<Relationship> relationships,
            List<String> extendedDataTableNames,
            String propertyIri,
            String displayName,
            PropertyType dataType,
            Map<String, String> possibleValues,
            Collection<TextIndexHint> textIndexHints,
            boolean userVisible,
            boolean searchable,
            boolean addable,
            boolean sortable,
            Integer sortPriority,
            String displayType,
            String propertyGroup,
            Double boost,
            String validationFormula,
            String displayFormula,
            ImmutableList<String> dependentPropertyIris,
            String[] intents,
            boolean deleteable,
            boolean updateable,
            User user,
            String workspaceId
    ) {
        if (CollectionUtils.isEmpty(extendedDataTableNames)
                && CollectionUtils.isEmpty(concepts)
                && CollectionUtils.isEmpty(relationships)) {
            throw new VisalloException("Must specify concepts or relationships to add property");
        }
        Vertex vertex = getOrCreatePropertyVertex(
                propertyIri,
                dataType,
                textIndexHints,
                sortable,
                boost,
                possibleValues,
                concepts,
                relationships,
                extendedDataTableNames,
                user,
                workspaceId
        );
        checkNotNull(vertex, "Could not find property: " + propertyIri);
        String vertexId = vertex.getId();

        boolean finalSearchable = determineSearchable(propertyIri, dataType, textIndexHints, searchable);

        try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(getPriority(user), user, getAuthorizations(workspaceId))) {
            ctx.setPushOnQueue(false);

            Date modifiedDate = new Date();
            Visibility visibility = VISIBILITY.getVisibility();
            Metadata metadata = getMetadata(modifiedDate, user, visibility);

            vertex = ctx.update(vertex.prepareMutation(), elemCtx -> {
                OntologyProperties.SEARCHABLE.updateProperty(elemCtx, finalSearchable, metadata, visibility);
                OntologyProperties.SORTABLE.updateProperty(elemCtx, sortable, metadata, visibility);
                OntologyProperties.ADDABLE.updateProperty(elemCtx, addable, metadata, visibility);
                OntologyProperties.DELETEABLE.updateProperty(elemCtx, deleteable, metadata, visibility);
                OntologyProperties.UPDATEABLE.updateProperty(elemCtx, updateable, metadata, visibility);
                OntologyProperties.USER_VISIBLE.updateProperty(elemCtx, userVisible, metadata, visibility);
                if (sortPriority != null) {
                    OntologyProperties.SORT_PRIORITY.updateProperty(elemCtx, sortPriority, metadata, visibility);
                }
                if (boost != null) {
                    OntologyProperties.BOOST.updateProperty(elemCtx, boost, metadata, visibility);
                }
                if (displayName != null && !displayName.trim().isEmpty()) {
                    OntologyProperties.DISPLAY_NAME.updateProperty(elemCtx, displayName.trim(), metadata, visibility);
                }
                if (displayType != null && !displayType.trim().isEmpty()) {
                    OntologyProperties.DISPLAY_TYPE.updateProperty(elemCtx, displayType, metadata, visibility);
                }
                if (propertyGroup != null && !propertyGroup.trim().isEmpty()) {
                    OntologyProperties.PROPERTY_GROUP.updateProperty(elemCtx, propertyGroup, metadata, visibility);
                }
                if (validationFormula != null && !validationFormula.trim().isEmpty()) {
                    OntologyProperties.VALIDATION_FORMULA.updateProperty(elemCtx, validationFormula, metadata, visibility);
                }
                if (displayFormula != null && !displayFormula.trim().isEmpty()) {
                    OntologyProperties.DISPLAY_FORMULA.updateProperty(elemCtx, displayFormula, metadata, visibility);
                }
                if (dependentPropertyIris != null) {
                    saveDependentProperties(vertexId, dependentPropertyIris, user, workspaceId);
                }
                if (possibleValues != null) {
                    OntologyProperties.POSSIBLE_VALUES.updateProperty(elemCtx, JSONUtil.toJson(possibleValues), metadata, visibility);
                }
                if (intents != null) {
                    for (String intent : intents) {
                        OntologyProperties.INTENT.updateProperty(elemCtx, intent, intent, metadata, visibility);
                    }
                }
            }).get();

            return createOntologyProperty(vertex, dependentPropertyIris, dataType, workspaceId);
        } catch (Exception e) {
            throw new VisalloException("Could not create property: " + propertyIri, e);
        }
    }

    @Override
    protected void getOrCreateInverseOfRelationship(Relationship fromRelationship, Relationship inverseOfRelationship) {
        checkNotNull(fromRelationship, "fromRelationship is required");
        checkNotNull(fromRelationship, "inverseOfRelationship is required");

        VertexiumRelationship fromRelationshipSg = (VertexiumRelationship) fromRelationship;
        VertexiumRelationship inverseOfRelationshipSg = (VertexiumRelationship) inverseOfRelationship;

        Vertex fromVertex = fromRelationshipSg.getVertex();
        checkNotNull(fromVertex, "fromVertex is required");

        Vertex inverseVertex = inverseOfRelationshipSg.getVertex();
        checkNotNull(inverseVertex, "inverseVertex is required");

        User user = getSystemUser();
        findOrAddEdge(fromVertex, inverseVertex, LabelName.INVERSE_OF.toString(), user, null);
        findOrAddEdge(inverseVertex, fromVertex, LabelName.INVERSE_OF.toString(), user, null);
    }

    @Override
    protected Relationship internalGetOrCreateRelationshipType(
            Relationship parent,
            Iterable<Concept> domainConcepts,
            Iterable<Concept> rangeConcepts,
            String relationshipIRI,
            String displayName,
            boolean isDeclaredInOntology,
            User user,
            String workspaceId
    ) {
        Relationship relationship = getRelationshipByIRI(relationshipIRI, workspaceId);
        try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(getPriority(user), user, getAuthorizations(workspaceId))) {
            ctx.setPushOnQueue(false);
            if (relationship != null) {
                if (isDeclaredInOntology) {
                    deleteChangeableProperties(relationship, getAuthorizations(workspaceId));
                }

                Vertex relationshipVertex = ((VertexiumRelationship) relationship).getVertex();
                for (Concept domainConcept : domainConcepts) {
                    if (!relationship.getDomainConceptIRIs().contains(domainConcept.getIRI())) {
                        findOrAddEdge(ctx, ((VertexiumConcept) domainConcept).getVertex(), relationshipVertex, LabelName.HAS_EDGE.toString());
                    }
                }
                for (Concept rangeConcept : rangeConcepts) {
                    if (!relationship.getRangeConceptIRIs().contains(rangeConcept.getIRI())) {
                        findOrAddEdge(ctx, relationshipVertex, ((VertexiumConcept) rangeConcept).getVertex(), LabelName.HAS_EDGE.toString());
                    }
                }

                return relationship;
            }


            Visibility visibility = VISIBILITY.getVisibility();
            VisibilityJson visibilityJson = new VisibilityJson(visibility.getVisibilityString());

            VertexBuilder builder = prepareVertex(ID_PREFIX_RELATIONSHIP, relationshipIRI, workspaceId, visibility, visibilityJson);

            Date modifiedDate = new Date();
            Vertex relationshipVertex = ctx.update(builder, modifiedDate, visibilityJson, TYPE_RELATIONSHIP, elemCtx -> {
                Metadata metadata = getMetadata(modifiedDate, user, visibility);
                OntologyProperties.ONTOLOGY_TITLE.updateProperty(elemCtx, relationshipIRI, metadata, visibility);
                if (displayName != null) {
                    OntologyProperties.DISPLAY_NAME.updateProperty(elemCtx, displayName, metadata, visibility);
                }
            }).get();


            validateRelationship(relationshipIRI, domainConcepts, rangeConcepts);

            for (Concept domainConcept : domainConcepts) {
                findOrAddEdge(ctx, ((VertexiumConcept) domainConcept).getVertex(), relationshipVertex, LabelName.HAS_EDGE.toString());
            }

            for (Concept rangeConcept : rangeConcepts) {
                findOrAddEdge(ctx, relationshipVertex, ((VertexiumConcept) rangeConcept).getVertex(), LabelName.HAS_EDGE.toString());
            }

            if (parent != null) {
                findOrAddEdge(ctx, relationshipVertex, ((VertexiumRelationship) parent).getVertex(), LabelName.IS_A.toString());
            }

            List<String> inverseOfIRIs = new ArrayList<>(); // no inverse of because this relationship is new

            List<String> domainConceptIris = Lists.newArrayList(new ConvertingIterable<Concept, String>(domainConcepts) {
                @Override
                protected String convert(Concept o) {
                    return o.getIRI();
                }
            });

            List<String> rangeConceptIris = Lists.newArrayList(new ConvertingIterable<Concept, String>(rangeConcepts) {
                @Override
                protected String convert(Concept o) {
                    return o.getIRI();
                }
            });

            if (!isPublic(workspaceId)) {
                findOrAddEdge(ctx, workspaceId, relationshipVertex.getId(), WorkspaceProperties.WORKSPACE_TO_ONTOLOGY_RELATIONSHIP_IRI);
            }

            Collection<OntologyProperty> properties = new ArrayList<>();
            String parentIRI = parent == null ? null : parent.getIRI();
            return createRelationship(parentIRI, relationshipVertex, inverseOfIRIs, domainConceptIris, rangeConceptIris, properties, workspaceId);
        } catch (Exception ex) {
            throw new VisalloException("Could not create relationship: " + relationshipIRI, ex);
        }
    }

    private Vertex getOrCreatePropertyVertex(
            final String propertyIri,
            final PropertyType dataType,
            Collection<TextIndexHint> textIndexHints,
            boolean sortable,
            Double boost,
            Map<String, String> possibleValues,
            List<Concept> concepts,
            List<Relationship> relationships,
            List<String> extendedDataTableNames,
            User user,
            String workspaceId
    ) {
        Authorizations authorizations = getAuthorizations(workspaceId);

        OntologyProperty typeProperty = getPropertyByIRI(propertyIri, workspaceId);
        Vertex propertyVertex;
        if (typeProperty == null) {
            definePropertyOnGraph(graph, propertyIri, PropertyType.getTypeClass(dataType), textIndexHints, boost, sortable);

            try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(getPriority(user), user, authorizations)) {
                ctx.setPushOnQueue(false);

                Visibility visibility = VISIBILITY.getVisibility();
                VisibilityJson visibilityJson = new VisibilityJson(visibility.getVisibilityString());

                VertexBuilder builder = prepareVertex(ID_PREFIX_PROPERTY, propertyIri, workspaceId, visibility, visibilityJson);
                Date modifiedDate = new Date();
                propertyVertex = ctx.update(builder, modifiedDate, visibilityJson, TYPE_PROPERTY, elemCtx -> {
                    Metadata metadata = getMetadata(modifiedDate, user, visibility);
                    OntologyProperties.ONTOLOGY_TITLE.updateProperty(elemCtx, propertyIri, metadata, visibility);
                    OntologyProperties.DATA_TYPE.updateProperty(elemCtx, dataType.toString(), metadata, visibility);
                    if (possibleValues != null) {
                        OntologyProperties.POSSIBLE_VALUES.updateProperty(elemCtx, JSONUtil.toJson(possibleValues), metadata, visibility);
                    }
                    if (textIndexHints != null && textIndexHints.size() > 0) {
                        textIndexHints.forEach(i -> {
                            String textIndexHint = i.toString();
                            OntologyProperties.TEXT_INDEX_HINTS.updateProperty(elemCtx, textIndexHint, textIndexHint, metadata, visibility);
                        });
                    }
                }).get();

                for (Concept concept : concepts) {
                    checkNotNull(concept, "concepts cannot have null values");
                    findOrAddEdge(ctx, ((VertexiumConcept) concept).getVertex(), propertyVertex, LabelName.HAS_PROPERTY.toString());
                }
                for (Relationship relationship : relationships) {
                    checkNotNull(relationship, "relationships cannot have null values");
                    findOrAddEdge(ctx, ((VertexiumRelationship) relationship).getVertex(), propertyVertex, LabelName.HAS_PROPERTY.toString());
                }
                if (extendedDataTableNames != null) {
                    for (String extendedDataTableName : extendedDataTableNames) {
                        checkNotNull(extendedDataTableName, "extendedDataTableNames cannot have null values");
                        OntologyProperty tableProperty = getPropertyByIRI(extendedDataTableName, workspaceId);
                        checkNotNull(tableProperty, "Could not find extended data property: " + extendedDataTableName);
                        if (!(tableProperty instanceof VertexiumExtendedDataTableOntologyProperty)) {
                            throw new VisalloException("Found property " + extendedDataTableName + " but was expecting "
                                    + "an extended data table property, check that the range is set to "
                                    + OWLOntologyUtil.EXTENDED_DATA_TABLE_IRI);
                        }
                        VertexiumExtendedDataTableOntologyProperty extendedDataTableProperty = (VertexiumExtendedDataTableOntologyProperty) tableProperty;
                        Vertex extendedDataTableVertex = extendedDataTableProperty.getVertex();
                        findOrAddEdge(ctx, extendedDataTableVertex, propertyVertex, LabelName.HAS_PROPERTY.toString());
                        extendedDataTableProperty.addProperty(propertyIri);
                    }
                }

                if (!isPublic(workspaceId)) {
                    findOrAddEdge(ctx, workspaceId, propertyVertex.getId(), WorkspaceProperties.WORKSPACE_TO_ONTOLOGY_RELATIONSHIP_IRI);
                }
            } catch (Exception e) {
                throw new VisalloException("Could not getOrCreatePropertyVertex: " + propertyIri, e);
            }
        } else {
            propertyVertex = ((VertexiumOntologyProperty) typeProperty).getVertex();
            deleteChangeableProperties(typeProperty, authorizations);
        }
        return propertyVertex;
    }

    private Priority getPriority(User user) {
        return user == null ? Priority.LOW : Priority.NORMAL;
    }

    private void saveDependentProperties(String propertyVertexId, Collection<String> dependentPropertyIris, User user, String workspaceId) {
        Authorizations authorizations = getAuthorizations(workspaceId);

        for (int i = 0; i < 1000; i++) {
            String edgeId = propertyVertexId + "-dependentProperty-" + i;
            Edge edge = graph.getEdge(edgeId, authorizations);
            if (edge == null) {
                break;
            }
            graph.deleteEdge(edge, authorizations);
        }
        graph.flush();

        try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(getPriority(user), user, authorizations)) {
            ctx.setPushOnQueue(false);

            Visibility visibility = VISIBILITY.getVisibility();
            VisibilityJson visibilityJson = new VisibilityJson(visibility.getVisibilityString());
            Date modifiedDate = new Date();
            Metadata metadata = getMetadata(modifiedDate, user, visibility);
            AtomicInteger indexCounter = new AtomicInteger();
            for (String dependentPropertyIri : dependentPropertyIris) {
                int i = indexCounter.getAndIncrement();
                String dependentPropertyVertexId = ID_PREFIX_PROPERTY + dependentPropertyIri;
                String edgeId = propertyVertexId + "-dependentProperty-" + i;
                EdgeBuilderByVertexId m = graph.prepareEdge(edgeId, propertyVertexId, dependentPropertyVertexId, OntologyProperties.EDGE_LABEL_DEPENDENT_PROPERTY, visibility);
                ctx.update(m, edgeCtx -> {
                    edgeCtx.updateBuiltInProperties(modifiedDate, visibilityJson);
                    OntologyProperties.DEPENDENT_PROPERTY_ORDER_PROPERTY_NAME.updateProperty(edgeCtx, i, metadata, visibility);
                });
            }
        }
    }

    @Override
    public void updatePropertyDependentIris(OntologyProperty property, Collection<String> newDependentPropertyIris, User user, String workspaceId) {
        VertexiumOntologyProperty vertexiumProperty = (VertexiumOntologyProperty) property;
        if (!isPublic(workspaceId) || property.getSandboxStatus() == SandboxStatus.PRIVATE) {
            throw new UnsupportedOperationException("Sandboxed updating of dependent iris is not currently supported for properties");
        }

        saveDependentProperties(vertexiumProperty.getVertex().getId(), newDependentPropertyIris, user, workspaceId);
        graph.flush();
        vertexiumProperty.setDependentProperties(newDependentPropertyIris);
    }

    @Override
    public void updatePropertyDomainIris(OntologyProperty property, Set<String> domainIris, User user, String workspaceId) {
        VertexiumOntologyProperty vertexiumProperty = (VertexiumOntologyProperty) property;
        if (!isPublic(workspaceId) && property.getSandboxStatus() != SandboxStatus.PRIVATE) {
            throw new UnsupportedOperationException("Sandboxed updating of domain iris is not currently supported for published properties");
        }

        Iterable<EdgeVertexPair> existingConcepts = vertexiumProperty.getVertex().getEdgeVertexPairs(Direction.BOTH, LabelName.HAS_PROPERTY.toString(), getAuthorizations(workspaceId));
        for (EdgeVertexPair existingConcept : existingConcepts) {
            String conceptIri = OntologyProperties.ONTOLOGY_TITLE.getPropertyValue(existingConcept.getVertex());
            if (!domainIris.remove(conceptIri)) {
                getGraph().softDeleteEdge(existingConcept.getEdge(), getAuthorizations(workspaceId));
            }
        }

        for (String domainIri : domainIris) {
            Vertex domainVertex;
            Concept concept = getConceptByIRI(domainIri, workspaceId);
            if (concept != null) {
                domainVertex = ((VertexiumConcept) concept).getVertex();
            } else {
                Relationship relationship = getRelationshipByIRI(domainIri, workspaceId);
                if (relationship != null) {
                    domainVertex = ((VertexiumRelationship) relationship).getVertex();
                } else {
                    throw new VisalloException("Could not find domain with IRI " + domainIri);
                }
            }
            findOrAddEdge(domainVertex, ((VertexiumOntologyProperty) property).getVertex(), LabelName.HAS_PROPERTY.toString(), user, workspaceId);
        }
    }

    private Vertex getParentVertex(Vertex vertex, String workspaceId) {
        try {
            return Iterables.getOnlyElement(vertex.getVertices(Direction.OUT, LabelName.IS_A.toString(), getAuthorizations(workspaceId)), null);
        } catch (IllegalArgumentException iae) {
            throw new IllegalStateException(String.format(
                    "Unexpected number of parents for concept %s",
                    OntologyProperties.TITLE.getPropertyValue(vertex)
            ), iae);
        }
    }

    protected Authorizations getAuthorizations(String workspaceId, String... otherAuthorizations) {
        if (isPublic(workspaceId) && (otherAuthorizations == null || otherAuthorizations.length == 0)) {
            return publicOntologyAuthorizations;
        }

        if (isPublic(workspaceId)) {
            return graph.createAuthorizations(publicOntologyAuthorizations, otherAuthorizations);
        } else if (otherAuthorizations == null || otherAuthorizations.length == 0) {
            return graph.createAuthorizations(publicOntologyAuthorizations, workspaceId);
        }
        return graph.createAuthorizations(publicOntologyAuthorizations, ArrayUtils.add(otherAuthorizations, workspaceId));
    }

    @Override
    protected Graph getGraph() {
        return graph;
    }

    /**
     * Overridable so subclasses can supply a custom implementation of OntologyProperty.
     */
    protected OntologyProperty createOntologyProperty(
            Vertex propertyVertex,
            ImmutableList<String> dependentPropertyIris,
            PropertyType propertyType,
            String workspaceId
    ) {
        if (propertyType.equals(PropertyType.EXTENDED_DATA_TABLE)) {
            Authorizations authorizations = getAuthorizations(workspaceId);
            VertexiumExtendedDataTableOntologyProperty result = new VertexiumExtendedDataTableOntologyProperty(propertyVertex, dependentPropertyIris, workspaceId);
            Iterable<String> tablePropertyIris = propertyVertex.getVertexIds(Direction.OUT, LabelName.HAS_PROPERTY.toString(), authorizations);
            for (String tablePropertyIri : tablePropertyIris) {
                result.addProperty(tablePropertyIri.substring(VertexiumOntologyRepository.ID_PREFIX_PROPERTY.length()));
            }
            return result;
        } else {
            return new VertexiumOntologyProperty(propertyVertex, dependentPropertyIris, workspaceId);
        }
    }

    /**
     * Overridable so subclasses can supply a custom implementation of Relationship.
     */
    protected Relationship createRelationship(
            String parentIRI,
            Vertex relationshipVertex,
            List<String> inverseOfIRIs,
            List<String> domainConceptIris,
            List<String> rangeConceptIris,
            Collection<OntologyProperty> properties,
            String workspaceId
    ) {
        return new VertexiumRelationship(
                parentIRI,
                relationshipVertex,
                domainConceptIris,
                rangeConceptIris,
                inverseOfIRIs,
                properties,
                workspaceId
        );
    }

    /**
     * Overridable so subclasses can supply a custom implementation of Concept.
     */
    protected Concept createConcept(Vertex vertex, List<OntologyProperty> conceptProperties, String parentConceptIRI, String workspaceId) {
        return new VertexiumConcept(vertex, parentConceptIRI, conceptProperties, workspaceId);
    }

    /**
     * Overridable so subclasses can supply a custom implementation of Concept.
     */
    protected Concept createConcept(Vertex vertex, String workspaceId) {
        return new VertexiumConcept(vertex, workspaceId);
    }

    @Override
    protected void deleteChangeableProperties(OntologyProperty property, Authorizations authorizations) {
        Vertex vertex = ((VertexiumOntologyProperty) property).getVertex();
        deleteChangeableProperties(vertex, authorizations);
    }

    @Override
    protected void deleteChangeableProperties(OntologyElement element, Authorizations authorizations) {
        Vertex vertex = element instanceof VertexiumConcept ? ((VertexiumConcept) element).getVertex() : ((VertexiumRelationship) element).getVertex();
        deleteChangeableProperties(vertex, authorizations);
    }

    private void deleteChangeableProperties(Vertex vertex, Authorizations authorizations) {
        for (Property property : vertex.getProperties()) {
            if (OntologyProperties.CHANGEABLE_PROPERTY_IRI.contains(property.getName())) {
                vertex.softDeleteProperty(property.getKey(), property.getName(), authorizations);
            }
        }
        graph.flush();
    }

    private List<OntologyProperty> transformProperties(Iterable<Vertex> vertices, String workspaceId) {
        return StreamSupport.stream(vertices.spliterator(), false)
                .filter(vertex -> VisalloProperties.CONCEPT_TYPE.getPropertyValue(vertex, "").equals(TYPE_PROPERTY))
                .map(vertex -> {
                    ImmutableList<String> dependentPropertyIris = getDependentPropertyIris(vertex, workspaceId);
                    PropertyType dataType = VertexiumOntologyProperty.getDataType(vertex);
                    return createOntologyProperty(vertex, dependentPropertyIris, dataType, workspaceId);
                })
                .collect(Collectors.toList());
    }

    private List<Concept> transformConcepts(Iterable<Vertex> vertices, String workspaceId) {
        Authorizations authorizations = getAuthorizations(workspaceId);

        List<Vertex> filtered = StreamSupport.stream(vertices.spliterator(), false)
                .filter(vertex -> VisalloProperties.CONCEPT_TYPE.getPropertyValue(vertex, "").equals(TYPE_CONCEPT))
                .collect(Collectors.toList());

        Map<String, String> parentVertexIdToIRI = buildParentIdToIriMap(filtered, authorizations);

        List<String> allPropertyVertexIds = filtered.stream()
                .flatMap(vertex ->
                        StreamSupport.stream(vertex.getVertexIds(Direction.OUT, LabelName.HAS_PROPERTY.toString(), authorizations).spliterator(), false)
                ).distinct().collect(Collectors.toList());
        List<OntologyProperty> ontologyProperties = transformProperties(getGraph().getVertices(allPropertyVertexIds, authorizations), workspaceId);
        Map<String, OntologyProperty> ontologyPropertiesByVertexId = ontologyProperties.stream()
                .collect(Collectors.toMap(
                        ontologyProperty -> ((VertexiumOntologyProperty) ontologyProperty).getVertex().getId(),
                        ontologyProperty -> ontologyProperty
                ));

        return filtered.stream().map(vertex -> {
            String parentVertexId = getParentVertexId(vertex, authorizations);
            String parentIRI = parentVertexId == null ? null : parentVertexIdToIRI.get(parentVertexId);

            Iterable<String> propertyVertexIds = vertex.getVertexIds(Direction.OUT, LabelName.HAS_PROPERTY.toString(), authorizations);
            List<OntologyProperty> conceptProperties = StreamSupport.stream(propertyVertexIds.spliterator(), false)
                    .map(ontologyPropertiesByVertexId::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            return createConcept(vertex, conceptProperties, parentIRI, workspaceId);
        }).collect(Collectors.toList());
    }

    private List<Relationship> transformRelationships(Iterable<Vertex> vertices, String workspaceId) {
        Authorizations authorizations = getAuthorizations(workspaceId);

        List<Vertex> filtered = StreamSupport.stream(vertices.spliterator(), false)
                .filter(vertex -> VisalloProperties.CONCEPT_TYPE.getPropertyValue(vertex, "").equals(TYPE_RELATIONSHIP))
                .collect(Collectors.toList());

        Set<String> allRelatedVertexIds = filtered.stream()
                .flatMap(vertex ->
                        StreamUtils.stream(
                                vertex.getVertexIds(Direction.OUT, LabelName.IS_A.toString(), authorizations),
                                vertex.getVertexIds(Direction.IN, LabelName.HAS_EDGE.toString(), authorizations),
                                vertex.getVertexIds(Direction.OUT, LabelName.HAS_EDGE.toString(), authorizations),
                                vertex.getVertexIds(Direction.OUT, LabelName.INVERSE_OF.toString(), authorizations)
                        )
                ).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> relatedVertexIdToIriMap = buildVertexIdToIriMap(allRelatedVertexIds, authorizations);

        List<String> allPropertyVertexIds = filtered.stream()
                .flatMap(vertex ->
                        StreamSupport.stream(vertex.getVertexIds(Direction.OUT, LabelName.HAS_PROPERTY.toString(), authorizations).spliterator(), false)
                ).distinct().collect(Collectors.toList());
        List<OntologyProperty> ontologyProperties = transformProperties(getGraph().getVertices(allPropertyVertexIds, authorizations), workspaceId);
        Map<String, OntologyProperty> ontologyPropertiesByVertexId = ontologyProperties.stream()
                .collect(Collectors.toMap(
                        ontologyProperty -> ((VertexiumOntologyProperty) ontologyProperty).getVertex().getId(),
                        ontologyProperty -> ontologyProperty
                ));

        return filtered.stream().map(vertex -> {
            String parentVertexId = getParentVertexId(vertex, authorizations);
            String parentIRI = parentVertexId == null ? null : relatedVertexIdToIriMap.get(parentVertexId);

            Iterable<String> propertyVertexIds = vertex.getVertexIds(Direction.OUT, LabelName.HAS_PROPERTY.toString(), authorizations);
            List<OntologyProperty> properties = StreamSupport.stream(propertyVertexIds.spliterator(), false)
                    .map(ontologyPropertiesByVertexId::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            return toVertexiumRelationship(parentIRI, vertex, properties, relatedVertexIdToIriMap, workspaceId);
        }).collect(Collectors.toList());
    }

    private String getParentVertexId(Vertex vertex, Authorizations authorizations) {
        Iterable<String> parentIds = vertex.getVertexIds(Direction.OUT, LabelName.IS_A.toString(), authorizations);
        return parentIds == null ? null : Iterables.getOnlyElement(parentIds, null);
    }

    private Map<String, String> buildParentIdToIriMap(Iterable<Vertex> vertices, Authorizations authorizations) {
        Set<String> parentVertexIds = StreamSupport.stream(vertices.spliterator(), false)
                .map(vertex -> getParentVertexId(vertex, authorizations))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return buildVertexIdToIriMap(parentVertexIds, authorizations);
    }

    private Map<String, String> buildVertexIdToIriMap(Iterable<String> vertexIds, Authorizations authorizations) {
        Iterable<Vertex> vertices = graph.getVertices(vertexIds, EnumSet.of(FetchHint.PROPERTIES), authorizations);
        try {
            return StreamSupport.stream(vertices.spliterator(), false)
                    .collect(Collectors.toMap(Vertex::getId, OntologyProperties.ONTOLOGY_TITLE::getPropertyValue));
        } finally {
            CloseableUtils.closeQuietly(vertices);
        }
    }

    @Override
    public void internalPublishConcept(Concept concept, User user, String workspaceId) {
        assert (concept instanceof VertexiumConcept);
        if (concept.getSandboxStatus() != SandboxStatus.PUBLIC) {
            Vertex vertex = ((VertexiumConcept) concept).getVertex();
            VisibilityJson visibilityJson = VisalloProperties.VISIBILITY_JSON.getPropertyValue(vertex);
            if (visibilityJson != null && visibilityJson.getWorkspaces().contains(workspaceId)) {
                visibilityJson = VisibilityJson.removeFromAllWorkspace(visibilityJson);
                VisalloVisibility visalloVisibility = visibilityTranslator.toVisibility(visibilityJson);
                try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(Priority.NORMAL, user, getAuthorizations(workspaceId))) {
                    ctx.update(vertex, new Date(), visibilityJson, null, vertexUpdateCtx -> {
                        ExistingElementMutation<Vertex> mutation = (ExistingElementMutation<Vertex>) vertexUpdateCtx.getMutation();
                        mutation.alterElementVisibility(visalloVisibility.getVisibility());
                    });
                    removeEdge(ctx, workspaceId, vertex.getId());
                }
            }
        }
    }

    @Override
    public void internalPublishRelationship(Relationship relationship, User user, String workspaceId) {
        assert (relationship instanceof VertexiumRelationship);
        if (relationship.getSandboxStatus() != SandboxStatus.PUBLIC) {
            Vertex vertex = ((VertexiumRelationship) relationship).getVertex();
            VisibilityJson visibilityJson = VisalloProperties.VISIBILITY_JSON.getPropertyValue(vertex);
            if (visibilityJson != null && visibilityJson.getWorkspaces().contains(workspaceId)) {
                visibilityJson = VisibilityJson.removeFromAllWorkspace(visibilityJson);
                VisalloVisibility visalloVisibility = visibilityTranslator.toVisibility(visibilityJson);
                try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(Priority.NORMAL, user, getAuthorizations(workspaceId))) {
                    ctx.update(vertex, new Date(), visibilityJson, null, vertexUpdateCtx -> {
                        ExistingElementMutation<Vertex> mutation = (ExistingElementMutation<Vertex>) vertexUpdateCtx.getMutation();
                        mutation.alterElementVisibility(visalloVisibility.getVisibility());
                    });
                    removeEdge(ctx, workspaceId, vertex.getId());
                }
            }
        }
    }

    @Override
    public void internalPublishProperty(OntologyProperty property, User user, String workspaceId) {
        assert (property instanceof VertexiumOntologyProperty);
        if (property.getSandboxStatus() != SandboxStatus.PUBLIC) {
            Vertex vertex = ((VertexiumOntologyProperty) property).getVertex();
            VisibilityJson visibilityJson = VisalloProperties.VISIBILITY_JSON.getPropertyValue(vertex);
            if (visibilityJson != null && visibilityJson.getWorkspaces().contains(workspaceId)) {
                visibilityJson = VisibilityJson.removeFromAllWorkspace(visibilityJson);
                VisalloVisibility visalloVisibility = visibilityTranslator.toVisibility(visibilityJson);
                try (GraphUpdateContext ctx = graphRepository.beginGraphUpdate(Priority.NORMAL, user, getAuthorizations(workspaceId))) {
                    ctx.update(vertex, new Date(), visibilityJson, null, vertexUpdateCtx -> {
                        ExistingElementMutation<Vertex> mutation = (ExistingElementMutation<Vertex>) vertexUpdateCtx.getMutation();
                        mutation.alterElementVisibility(visalloVisibility.getVisibility());
                    });
                    removeEdge(ctx, workspaceId, vertex.getId());
                }
            }
        }
    }
}
