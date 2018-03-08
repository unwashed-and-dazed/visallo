package org.visallo.vertexium.model.ontology;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.io.ReaderDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.vertexium.TextIndexHint;
import org.vertexium.inmemory.InMemoryAuthorizations;
import org.vertexium.util.ConvertingIterable;
import org.vertexium.util.IterableUtils;
import org.visallo.core.cache.CacheService;
import org.visallo.core.config.Configuration;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.lock.LockRepository;
import org.visallo.core.model.ontology.*;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.clientapi.model.PropertyType;
import org.visallo.web.clientapi.model.SandboxStatus;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.vertexium.util.IterableUtils.toList;

@Singleton
public class InMemoryOntologyRepository extends OntologyRepositoryBase {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(InMemoryOntologyRepository.class);
    private static final String PUBLIC_ONTOLOGY_CACHE_KEY = "InMemoryOntologyRepository.PUBLIC";

    private final Graph graph;

    private final Map<String, Map<String, InMemoryConcept>> conceptsCache = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Map<String, InMemoryRelationship>> relationshipsCache = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Map<String, InMemoryOntologyProperty>> propertiesCache = Collections.synchronizedMap(new HashMap<>());

    private final List<OwlData> fileCache = new ArrayList<>();

    @Inject
    public InMemoryOntologyRepository(
            Graph graph,
            Configuration configuration,
            LockRepository lockRepository,
            CacheService cacheService
    ) throws Exception {
        super(configuration, lockRepository, cacheService);
        this.graph = graph;

        clearCache();
        conceptsCache.put(PUBLIC_ONTOLOGY_CACHE_KEY, new HashMap<>());
        relationshipsCache.put(PUBLIC_ONTOLOGY_CACHE_KEY, new HashMap<>());
        propertiesCache.put(PUBLIC_ONTOLOGY_CACHE_KEY, new HashMap<>());

        loadOntologies(getConfiguration(), new InMemoryAuthorizations(VISIBILITY_STRING));
    }

    private Map<String, InMemoryConcept> computeConceptCacheForWorkspace(String workspaceId) {
        Map<String, InMemoryConcept> workspaceConcepts = computeCacheForWorkspace(conceptsCache, workspaceId);

        if (!isPublic(workspaceId) && propertiesCache.containsKey(workspaceId)) {
            propertiesCache.get(workspaceId).values().forEach(workspaceProperty ->
                    workspaceProperty.getConceptIris().forEach(conceptIri -> {
                        InMemoryConcept concept = workspaceConcepts.get(conceptIri);
                        if (concept.getSandboxStatus() == SandboxStatus.PUBLIC) {
                            concept = concept.shallowCopy();
                            concept.getProperties().add(workspaceProperty);
                            workspaceConcepts.put(conceptIri, concept);
                        }
                    }));
        }

        return workspaceConcepts;
    }

    private Map<String, InMemoryRelationship> computeRelationshipCacheForWorkspace(String workspaceId) {
        Map<String, InMemoryRelationship> workspaceRelationships = computeCacheForWorkspace(relationshipsCache, workspaceId);

        if (!isPublic(workspaceId) && propertiesCache.containsKey(workspaceId)) {
            propertiesCache.get(workspaceId).values().forEach(workspaceProperty ->
                    workspaceProperty.getRelationshipIris().forEach(relationshipIri -> {
                        InMemoryRelationship relationship = workspaceRelationships.get(relationshipIri);
                        if (relationship.getSandboxStatus() == SandboxStatus.PUBLIC) {
                            relationship = relationship.shallowCopy();
                            relationship.getProperties().add(workspaceProperty);
                            workspaceRelationships.put(relationshipIri, relationship);
                        }
                    }));
        }

        return workspaceRelationships;
    }

    private Map<String, InMemoryOntologyProperty> computePropertyCacheForWorkspace(String workspaceId) {
        return computeCacheForWorkspace(propertiesCache, workspaceId);
    }

    private <T> Map<String, T> computeCacheForWorkspace(Map<String, Map<String, T>> cache, String workspaceId) {
        Map<String, T> result = new HashMap<>();
        result.putAll(cache.compute(PUBLIC_ONTOLOGY_CACHE_KEY, (k, v) -> v == null ? new HashMap<>() : v));
        if (!isPublic(workspaceId) && cache.containsKey(workspaceId)) {
            result.putAll(cache.get(workspaceId));
        }
        return result;
    }

    @Override
    protected Concept importOntologyClass(
            OWLOntology o,
            OWLClass ontologyClass,
            File inDir,
            Authorizations authorizations
    ) throws IOException {
        InMemoryConcept concept = (InMemoryConcept) super.importOntologyClass(o, ontologyClass, inDir, authorizations);
        conceptsCache.get(PUBLIC_ONTOLOGY_CACHE_KEY).put(concept.getIRI(), concept);
        return concept;
    }

    @Override
    protected Relationship importObjectProperty(
            OWLOntology o,
            OWLObjectProperty objectProperty,
            Authorizations authorizations
    ) {
        InMemoryRelationship relationship = (InMemoryRelationship) super.importObjectProperty(
                o,
                objectProperty,
                authorizations
        );
        relationshipsCache.get(PUBLIC_ONTOLOGY_CACHE_KEY).put(relationship.getIRI(), relationship);
        return relationship;
    }

    @Override
    protected void setIconProperty(
            Concept concept,
            File inDir,
            String glyphIconFileName,
            String propertyKey,
            User user,
            Authorizations authorizations
    ) throws IOException {
        if (glyphIconFileName == null) {
            concept.setProperty(propertyKey, null, user, authorizations);
        } else {
            File iconFile = new File(inDir, glyphIconFileName);
            if (!iconFile.exists()) {
                throw new RuntimeException("Could not find icon file: " + iconFile.toString());
            }
            try {
                try (InputStream iconFileIn = new FileInputStream(iconFile)) {
                    concept.setProperty(propertyKey, IOUtils.toByteArray(iconFileIn), user, authorizations);
                }
            } catch (IOException ex) {
                throw new VisalloException("Failed to set glyph icon to " + iconFile, ex);
            }
        }
    }

    @Override
    protected void addEntityGlyphIconToEntityConcept(Concept entityConcept, byte[] rawImg, Authorizations authorizations) {
        entityConcept.setProperty(OntologyProperties.GLYPH_ICON.getPropertyName(), rawImg, getSystemUser(), authorizations);
    }

    @Override
    protected void storeOntologyFile(InputStream inputStream, IRI documentIRI, Authorizations authorizations) {
        try {
            byte[] inFileData = IOUtils.toByteArray(inputStream);
            synchronized (fileCache) {
                fileCache.add(new OwlData(documentIRI.toString(), inFileData));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Deprecated
    @Override
    public boolean isOntologyDefined(String iri) {
        synchronized (fileCache) {
            for (OwlData owlData : fileCache) {
                if (owlData.iri.equals(iri)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public void updatePropertyDependentIris(OntologyProperty property, Collection<String> dependentPropertyIris, User user, String workspaceId) {
        if (!isPublic(workspaceId) || property.getSandboxStatus() == SandboxStatus.PRIVATE) {
            throw new UnsupportedOperationException("Sandboxed updating of dependent iris is not currently supported for properties");
        }

        InMemoryOntologyProperty inMemoryOntologyProperty = (InMemoryOntologyProperty) property;
        inMemoryOntologyProperty.setDependentPropertyIris(dependentPropertyIris);
    }

    @Override
    protected List<OWLOntology> loadOntologyFiles(
            OWLOntologyManager m,
            OWLOntologyLoaderConfiguration config,
            IRI excludedIRI
    ) throws Exception {
        List<OWLOntology> loadedOntologies = new ArrayList<>();
        List<OwlData> fileCacheCopy;
        synchronized (fileCache) {
            fileCacheCopy = ImmutableList.copyOf(fileCache);
        }
        for (OwlData owlData : fileCacheCopy) {
            IRI visalloBaseOntologyIRI = IRI.create(owlData.iri);
            if (excludedIRI != null && excludedIRI.equals(visalloBaseOntologyIRI)) {
                continue;
            }
            try (InputStream visalloBaseOntologyIn = new ByteArrayInputStream(owlData.data)) {
                Reader visalloBaseOntologyReader = new InputStreamReader(visalloBaseOntologyIn);
                LOGGER.info("Loading existing ontology: %s", owlData.iri);
                OWLOntologyDocumentSource visalloBaseOntologySource = new ReaderDocumentSource(
                        visalloBaseOntologyReader,
                        visalloBaseOntologyIRI
                );
                OWLOntology o = m.loadOntologyFromOntologyDocument(visalloBaseOntologySource, config);
                loadedOntologies.add(o);
            }
        }
        return loadedOntologies;
    }

    @Override
    public void addDomainConceptsToRelationshipType(String relationshipIri, List<String> conceptIris, User user, String workspaceId) {
        InMemoryRelationship relationship = computeRelationshipCacheForWorkspace(workspaceId).get(relationshipIri);
        if (!isPublic(workspaceId) && relationship.getSandboxStatus() != SandboxStatus.PRIVATE) {
            throw new UnsupportedOperationException("Sandboxed updating of domain iris is not currently supported for published relationships");
        }

        List<String> missingConcepts = conceptIris.stream()
                .filter(c -> !relationship.getDomainConceptIRIs().contains(c))
                .collect(Collectors.toList());

        if (relationship.getSandboxStatus() == SandboxStatus.PRIVATE) {
            relationship.getDomainConceptIRIs().addAll(missingConcepts);
        } else {
            InMemoryRelationship inMemoryRelationship = relationship.shallowCopy();
            inMemoryRelationship.getDomainConceptIRIs().addAll(missingConcepts);

            Map<String, InMemoryRelationship> workspaceCache = relationshipsCache.compute(workspaceId, (k, v) -> v == null ? new HashMap<>() : v);
            workspaceCache.put(inMemoryRelationship.getIRI(), inMemoryRelationship);
        }
    }

    @Override
    public void addRangeConceptsToRelationshipType(String relationshipIri, List<String> conceptIris, User user, String workspaceId) {
        InMemoryRelationship relationship = computeRelationshipCacheForWorkspace(workspaceId).get(relationshipIri);
        if (!isPublic(workspaceId) && relationship.getSandboxStatus() != SandboxStatus.PRIVATE) {
            throw new UnsupportedOperationException("Sandboxed updating of range iris is not currently supported for published relationships");
        }

        List<String> missingConcepts = conceptIris.stream()
                .filter(c -> !relationship.getRangeConceptIRIs().contains(c))
                .collect(Collectors.toList());

        if (relationship.getSandboxStatus() == SandboxStatus.PRIVATE) {
            relationship.getRangeConceptIRIs().addAll(missingConcepts);
        } else {
            InMemoryRelationship inMemoryRelationship = relationship.shallowCopy();
            inMemoryRelationship.getRangeConceptIRIs().addAll(missingConcepts);

            Map<String, InMemoryRelationship> workspaceCache = relationshipsCache.compute(workspaceId, (k, v) -> v == null ? new HashMap<>() : v);
            workspaceCache.put(inMemoryRelationship.getIRI(), inMemoryRelationship);
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
            String workspaceId) {
        checkNotNull(concepts, "concept was null");
        InMemoryOntologyProperty property = getPropertyByIRI(propertyIri, workspaceId);
        if (property == null) {
            searchable = determineSearchable(propertyIri, dataType, textIndexHints, searchable);
            definePropertyOnGraph(graph, propertyIri, PropertyType.getTypeClass(dataType), textIndexHints, boost, sortable);

            if (dataType.equals(PropertyType.EXTENDED_DATA_TABLE)) {
                property = new InMemoryExtendedDataTableOntologyProperty();
            } else {
                property = new InMemoryOntologyProperty();
            }
            property.setDataType(dataType);
        } else {
            deleteChangeableProperties(property, null);
        }

        property.setUserVisible(userVisible);
        property.setSearchable(searchable);
        property.setAddable(addable);
        property.setSortable(sortable);
        property.setSortPriority(sortPriority);
        property.setTitle(propertyIri);
        property.setBoost(boost);
        property.setDisplayType(displayType);
        property.setPropertyGroup(propertyGroup);
        property.setValidationFormula(validationFormula);
        property.setDisplayFormula(displayFormula);
        property.setDeleteable(deleteable);
        property.setUpdateable(updateable);
        property.setWorkspaceId(isPublic(workspaceId) ? null : workspaceId);
        if (dependentPropertyIris != null && !dependentPropertyIris.isEmpty()) {
            property.setDependentPropertyIris(dependentPropertyIris);
        }
        if (intents != null) {
            for (String intent : intents) {
                property.addIntent(intent);
            }
        }
        if (displayName != null && !displayName.trim().isEmpty()) {
            property.setDisplayName(displayName);
        }
        if (textIndexHints != null && textIndexHints.size() > 0) {
            for (TextIndexHint textIndexHint : textIndexHints) {
                property.addTextIndexHints(textIndexHint.toString());
            }
        }
        property.setPossibleValues(possibleValues);

        String cacheKey = isPublic(workspaceId) ? PUBLIC_ONTOLOGY_CACHE_KEY : workspaceId;
        Map<String, InMemoryOntologyProperty> workspaceCache = propertiesCache.compute(cacheKey, (k, v) -> v == null ? new HashMap<>() : v);
        workspaceCache.put(propertyIri, property);

        for (Concept concept : concepts) {
            property.getConceptIris().add(concept.getIRI());
            if (isPublic(workspaceId) || concept.getSandboxStatus() == SandboxStatus.PRIVATE) {
                concept.getProperties().add(property);
            }
        }

        for (Relationship relationship : relationships) {
            property.getRelationshipIris().add(relationship.getIRI());
            if (isPublic(workspaceId) || relationship.getSandboxStatus() == SandboxStatus.PRIVATE) {
                relationship.getProperties().add(property);
            }
        }

        if (extendedDataTableNames != null) {
            for (String extendedDataTableName : extendedDataTableNames) {
                InMemoryExtendedDataTableOntologyProperty edtp = (InMemoryExtendedDataTableOntologyProperty) getPropertyByIRI(extendedDataTableName, workspaceId);
                edtp.addTableProperty(property.getIri());
            }
        }

        checkNotNull(property, "Could not find property: " + propertyIri);
        return property;
    }

    @Override
    public void updatePropertyDomainIris(OntologyProperty property, Set<String> domainIris, User user, String workspaceId) {
        if (!isPublic(workspaceId) && property.getSandboxStatus() != SandboxStatus.PRIVATE) {
            throw new UnsupportedOperationException("Sandboxed updating of domain iris is not currently supported for published properties");
        }

        InMemoryOntologyProperty inMemoryProperty = (InMemoryOntologyProperty) property;

        for (Concept concept : getConceptsWithProperties(workspaceId)) {
            if (concept.getProperties().contains(property)) {
                if (!domainIris.remove(concept.getIRI())) {
                    if (isPublic(workspaceId) || concept.getSandboxStatus() == SandboxStatus.PRIVATE) {
                        concept.getProperties().remove(property);
                    }
                    inMemoryProperty.getConceptIris().remove(concept.getIRI());
                }
            }
        }
        for (Relationship relationship : getRelationships(workspaceId)) {
            if (relationship.getProperties().contains(property)) {
                if (!domainIris.remove(relationship.getIRI())) {
                    if (isPublic(workspaceId) || relationship.getSandboxStatus() == SandboxStatus.PRIVATE) {
                        relationship.getProperties().remove(property);
                    }
                    inMemoryProperty.getRelationshipIris().remove(relationship.getIRI());
                }
            }
        }

        for (String domainIri : domainIris) {
            InMemoryConcept concept = getConceptByIRI(domainIri, workspaceId);
            if (concept != null) {
                if (isPublic(workspaceId) || concept.getSandboxStatus() == SandboxStatus.PRIVATE) {
                    concept.getProperties().add(property);
                }
                inMemoryProperty.getConceptIris().add(concept.getIRI());
            } else {
                InMemoryRelationship relationship = getRelationshipByIRI(domainIri, workspaceId);
                if (relationship != null) {
                    if (isPublic(workspaceId) || relationship.getSandboxStatus() == SandboxStatus.PRIVATE) {
                        relationship.getProperties().add(property);
                    }
                    inMemoryProperty.getRelationshipIris().add(relationship.getIRI());
                } else {
                    throw new VisalloException("Could not find domain with IRI " + domainIri);
                }
            }
        }
    }

    @Override
    public void internalPublishConcept(Concept concept, User user, String workspaceId) {
        if (conceptsCache.containsKey(workspaceId)) {
            Map<String, InMemoryConcept> sandboxedConcepts = conceptsCache.get(workspaceId);
            if (sandboxedConcepts.containsKey(concept.getIRI())) {
                InMemoryConcept sandboxConcept = sandboxedConcepts.remove(concept.getIRI());
                sandboxConcept.removeWorkspaceId();
                conceptsCache.get(PUBLIC_ONTOLOGY_CACHE_KEY).put(concept.getIRI(), sandboxConcept);
            }
        }
    }

    @Override
    public void internalPublishRelationship(Relationship relationship, User user, String workspaceId) {
        if (relationshipsCache.containsKey(workspaceId)) {
            Map<String, InMemoryRelationship> sandboxedRelationships = relationshipsCache.get(workspaceId);
            if (sandboxedRelationships.containsKey(relationship.getIRI())) {
                InMemoryRelationship sandboxRelationship = sandboxedRelationships.remove(relationship.getIRI());
                sandboxRelationship.removeWorkspaceId();
                relationshipsCache.get(PUBLIC_ONTOLOGY_CACHE_KEY).put(relationship.getIRI(), sandboxRelationship);
            }
        }
    }

    @Override
    public void internalPublishProperty(OntologyProperty property, User user, String workspaceId) {
        if (propertiesCache.containsKey(workspaceId)) {
            Map<String, InMemoryOntologyProperty> sandboxedProperties = propertiesCache.get(workspaceId);
            if (sandboxedProperties.containsKey(property.getIri())) {
                InMemoryOntologyProperty sandboxProperty = sandboxedProperties.remove(property.getIri());
                sandboxProperty.removeWorkspaceId();
                propertiesCache.get(PUBLIC_ONTOLOGY_CACHE_KEY).put(property.getIri(), sandboxProperty);

                Map<String, InMemoryConcept> publicConcepts = conceptsCache.get(PUBLIC_ONTOLOGY_CACHE_KEY);
                sandboxProperty.getConceptIris().forEach(c -> publicConcepts.get(c).getProperties().add(sandboxProperty));

                Map<String, InMemoryRelationship> publicRelationships = relationshipsCache.get(PUBLIC_ONTOLOGY_CACHE_KEY);
                sandboxProperty.getRelationshipIris().forEach(r -> publicRelationships.get(r).getProperties().add(sandboxProperty));
            }
        }
    }

    @Override
    protected void getOrCreateInverseOfRelationship(Relationship fromRelationship, Relationship inverseOfRelationship) {
        InMemoryRelationship fromRelationshipMem = (InMemoryRelationship) fromRelationship;
        InMemoryRelationship inverseOfRelationshipMem = (InMemoryRelationship) inverseOfRelationship;

        fromRelationshipMem.addInverseOf(inverseOfRelationshipMem);
        inverseOfRelationshipMem.addInverseOf(fromRelationshipMem);
    }

    @Override
    public Iterable<Relationship> getRelationships(String workspaceId) {
        return new ArrayList<>(computeRelationshipCacheForWorkspace(workspaceId).values());
    }

    @Override
    public Iterable<Relationship> getRelationships(Iterable<String> ids, String workspaceId) {
        if (ids != null) {
            List<String> idList = IterableUtils.toList(ids);
            Iterable<Relationship> workspaceRelationships = getRelationships(workspaceId);
            return StreamSupport.stream(workspaceRelationships.spliterator(), true)
                    .filter(r -> idList.contains(r.getId()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public Iterable<OntologyProperty> getProperties(Iterable<String> ids, String workspaceId) {
        if (ids != null) {
            List<String> idList = IterableUtils.toList(ids);
            Iterable<OntologyProperty> workspaceProps = getProperties(workspaceId);
            return StreamSupport.stream(workspaceProps.spliterator(), true)
                    .filter(p -> idList.contains(p.getId()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public Iterable<OntologyProperty> getProperties(String workspaceId) {
        return new ArrayList<>(computePropertyCacheForWorkspace(workspaceId).values());
    }

    @Override
    public String getDisplayNameForLabel(String relationshipIRI, String workspaceId) {
        InMemoryRelationship relationship = computeRelationshipCacheForWorkspace(workspaceId).get(relationshipIRI);
        checkNotNull(relationship, "Could not find relationship " + relationshipIRI);
        return relationship.getDisplayName();
    }

    @Override
    public InMemoryOntologyProperty getPropertyByIRI(String propertyIRI, String workspaceId) {
        return computePropertyCacheForWorkspace(workspaceId).get(propertyIRI);
    }

    @Override
    public InMemoryRelationship getRelationshipByIRI(String relationshipIRI, String workspaceId) {
        return computeRelationshipCacheForWorkspace(workspaceId).get(relationshipIRI);
    }

    @Override
    public InMemoryConcept getConceptByIRI(String conceptIRI, String workspaceId) {
        return computeConceptCacheForWorkspace(workspaceId).get(conceptIRI);
    }

    @Override
    public boolean hasRelationshipByIRI(String relationshipIRI, String workspaceId) {
        return getRelationshipByIRI(relationshipIRI, workspaceId) != null;
    }

    @Override
    public Iterable<Concept> getConceptsWithProperties(String workspaceId) {
        return new ArrayList<>(computeConceptCacheForWorkspace(workspaceId).values());
    }

    @Override
    public Concept getRootConcept(String workspaceId) {
        return computeConceptCacheForWorkspace(workspaceId).get(InMemoryOntologyRepository.ROOT_CONCEPT_IRI);
    }

    @Override
    public Concept getEntityConcept(String workspaceId) {
        return computeConceptCacheForWorkspace(workspaceId).get(InMemoryOntologyRepository.ENTITY_CONCEPT_IRI);
    }

    @Override
    public Concept getParentConcept(Concept concept, String workspaceId) {
        return computeConceptCacheForWorkspace(workspaceId).get(concept.getParentConceptIRI());
    }

    @Override
    public Relationship getParentRelationship(Relationship relationship, String workspaceId) {
        return computeRelationshipCacheForWorkspace(workspaceId).get(relationship.getParentIRI());
    }

    @Override
    public Iterable<Concept> getConcepts(Iterable<String> ids, String workspaceId) {
        if (ids != null) {
            List<String> idList = IterableUtils.toList(ids);
            Iterable<Concept> workspaceConcepts = getConceptsWithProperties(workspaceId);
            return StreamSupport.stream(workspaceConcepts.spliterator(), true)
                    .filter(c -> idList.contains(c.getId()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    protected void internalDeleteConcept(Concept concept, String workspaceId) {
        String cacheKey = workspaceId;
        Map<String, InMemoryConcept> workspaceCache = conceptsCache.compute(cacheKey, (k, v) -> v == null ? new HashMap<>() : v);
        workspaceCache.remove(concept.getIRI());

        for (OntologyProperty property : getProperties(workspaceId)) {
            property.getConceptIris().remove(concept.getIRI());
        }
    }

    @Override
    protected void internalDeleteProperty(OntologyProperty property, String workspaceId) {
        String cacheKey = workspaceId;
        Map<String, InMemoryOntologyProperty> workspaceCache = propertiesCache.compute(cacheKey, (k, v) -> v == null ? new HashMap<>() : v);
        workspaceCache.remove(property.getIri());
    }

    @Override
    protected void internalDeleteRelationship(Relationship relationship, String workspaceId) {
        String cacheKey = workspaceId;
        Map<String, InMemoryRelationship> workspaceCache = relationshipsCache.compute(cacheKey, (k, v) -> v == null ? new HashMap<>() : v);
        workspaceCache.remove(relationship.getIRI());

        for (OntologyProperty property : getProperties(workspaceId)) {
            property.getRelationshipIris().remove(relationship.getIRI());
        }
    }

    @Override
    protected List<Concept> getChildConcepts(Concept concept, String workspaceId) {
        Map<String, InMemoryConcept> workspaceConcepts = computeConceptCacheForWorkspace(workspaceId);
        return workspaceConcepts.values().stream()
                .filter(workspaceConcept -> concept.getIRI().equals(workspaceConcept.getParentConceptIRI()))
                .collect(Collectors.toList());
    }

    @Override
    protected List<Relationship> getChildRelationships(Relationship relationship, String workspaceId) {
        Map<String, InMemoryRelationship> workspaceRelationships = computeRelationshipCacheForWorkspace(workspaceId);
        return workspaceRelationships.values().stream()
                .filter(workspaceRelationship -> relationship.getIRI().equals(workspaceRelationship.getParentIRI()))
                .collect(Collectors.toList());
    }

    @Override
    protected Concept internalGetOrCreateConcept(Concept parent, String conceptIRI, String displayName, String glyphIconHref, String color, File inDir, boolean deleteChangeableProperties, User user, String workspaceId) {
        InMemoryConcept concept = getConceptByIRI(conceptIRI, workspaceId);

        if (concept != null) {
            if (deleteChangeableProperties) {
                deleteChangeableProperties(concept, null);
            }
            return concept;
        }
        if (parent == null) {
            concept = new InMemoryConcept(conceptIRI, null, isPublic(workspaceId) ? null : workspaceId);
        } else {
            concept = new InMemoryConcept(conceptIRI, ((InMemoryConcept) parent).getConceptIRI(), isPublic(workspaceId) ? null : workspaceId);
        }
        concept.setProperty(OntologyProperties.TITLE.getPropertyName(), conceptIRI, user, null);
        concept.setProperty(OntologyProperties.DISPLAY_NAME.getPropertyName(), displayName, user, null);

        if (conceptIRI.equals(OntologyRepository.ENTITY_CONCEPT_IRI)) {
            concept.setProperty(OntologyProperties.TITLE_FORMULA.getPropertyName(), "prop('http://visallo.org#title') || ''", user, null);

            // TODO: change to ontology && ontology.displayName
            concept.setProperty(OntologyProperties.SUBTITLE_FORMULA.getPropertyName(), "prop('http://visallo.org#source') || ''", user, null);
            concept.setProperty(OntologyProperties.TIME_FORMULA.getPropertyName(), "''", user, null);
        }

        if (!StringUtils.isEmpty(glyphIconHref)) {
            concept.setProperty(OntologyProperties.GLYPH_ICON_FILE_NAME.getPropertyName(), glyphIconHref, user, null);
        }
        if (!StringUtils.isEmpty(color)) {
            concept.setProperty(OntologyProperties.COLOR.getPropertyName(), color, user, null);
        }

        String cacheKey = isPublic(workspaceId) ? PUBLIC_ONTOLOGY_CACHE_KEY : workspaceId;
        Map<String, InMemoryConcept> workspaceCache = conceptsCache.compute(cacheKey, (k, v) -> v == null ? new HashMap<>() : v);
        workspaceCache.put(conceptIRI, concept);

        return concept;
    }

    @Override
    protected Relationship internalGetOrCreateRelationshipType(
            Relationship parent,
            Iterable<Concept> domainConcepts,
            Iterable<Concept> rangeConcepts,
            String relationshipIRI,
            String displayName,
            boolean deleteChangeableProperties,
            User user,
            String workspaceId
    ) {
        Relationship relationship = getRelationshipByIRI(relationshipIRI, workspaceId);
        if (relationship != null) {
            if (deleteChangeableProperties) {
                deleteChangeableProperties(relationship, null);
            }

            for (Concept domainConcept : domainConcepts) {
                if (!relationship.getDomainConceptIRIs().contains(domainConcept.getIRI())) {
                    relationship.getDomainConceptIRIs().add(domainConcept.getIRI());
                }
            }

            for (Concept rangeConcept : rangeConcepts) {
                if (!relationship.getRangeConceptIRIs().contains(rangeConcept.getIRI())) {
                    relationship.getRangeConceptIRIs().add(rangeConcept.getIRI());
                }
            }

            return relationship;
        }

        validateRelationship(relationshipIRI, domainConcepts, rangeConcepts);

        List<String> domainConceptIris = toList(new ConvertingIterable<Concept, String>(domainConcepts) {
            @Override
            protected String convert(Concept o) {
                return o.getIRI();
            }
        });

        List<String> rangeConceptIris = toList(new ConvertingIterable<Concept, String>(rangeConcepts) {
            @Override
            protected String convert(Concept o) {
                return o.getIRI();
            }
        });

        String parentIRI = parent == null ? null : parent.getIRI();
        Collection<OntologyProperty> properties = new ArrayList<>();
        InMemoryRelationship inMemRelationship = new InMemoryRelationship(
                parentIRI,
                relationshipIRI,
                domainConceptIris,
                rangeConceptIris,
                properties,
                isPublic(workspaceId) ? null : workspaceId
        );

        if (displayName != null) {
            inMemRelationship.setProperty(OntologyProperties.DISPLAY_NAME.getPropertyName(), displayName, user, getAuthorizations(workspaceId));
        }

        String cacheKey = isPublic(workspaceId) ? PUBLIC_ONTOLOGY_CACHE_KEY : workspaceId;
        Map<String, InMemoryRelationship> workspaceCache = relationshipsCache.compute(cacheKey, (k, v) -> v == null ? new HashMap<>() : v);
        workspaceCache.put(relationshipIRI, inMemRelationship);

        return inMemRelationship;
    }

    private static class OwlData {
        public final String iri;
        public final byte[] data;

        public OwlData(String iri, byte[] data) {
            this.iri = iri;
            this.data = data;
        }
    }

    protected Authorizations getAuthorizations(String workspaceId, String... otherAuthorizations) {
        if (isPublic(workspaceId) && (otherAuthorizations == null || otherAuthorizations.length == 0)) {
            return new InMemoryAuthorizations(VISIBILITY_STRING);
        }

        if (isPublic(workspaceId)) {
            return new InMemoryAuthorizations(ArrayUtils.add(otherAuthorizations, VISIBILITY_STRING));
        } else if (otherAuthorizations == null || otherAuthorizations.length == 0) {
            return new InMemoryAuthorizations(VISIBILITY_STRING, workspaceId);
        }
        return new InMemoryAuthorizations(ArrayUtils.addAll(otherAuthorizations, VISIBILITY_STRING, workspaceId));
    }

    protected Graph getGraph() {
        return graph;
    }

    @Override
    protected void deleteChangeableProperties(OntologyProperty property, Authorizations authorizations) {
        for (String propertyName : OntologyProperties.CHANGEABLE_PROPERTY_IRI) {
            if (OntologyProperties.INTENT.getPropertyName().equals(propertyName)) {
                for (String intent : property.getIntents()) {
                    property.removeIntent(intent, null);
                }
            } else {
                property.setProperty(propertyName, null, null, null);
            }
        }
    }

    @Override
    protected void deleteChangeableProperties(OntologyElement element, Authorizations authorizations) {
        for (String propertyName : OntologyProperties.CHANGEABLE_PROPERTY_IRI) {
            if (element instanceof InMemoryRelationship) {
                InMemoryRelationship inMemoryRelationship = (InMemoryRelationship) element;
                if (OntologyProperties.INTENT.getPropertyName().equals(propertyName)) {
                    for (String intent : inMemoryRelationship.getIntents()) {
                        inMemoryRelationship.removeIntent(intent, null);
                    }
                } else {
                    inMemoryRelationship.removeProperty(propertyName, null);
                }
            } else {
                InMemoryConcept inMemoryConcept = (InMemoryConcept) element;
                if (OntologyProperties.INTENT.getPropertyName().equals(propertyName)) {
                    for (String intent : inMemoryConcept.getIntents()) {
                        inMemoryConcept.removeIntent(intent, null);
                    }
                } else {
                    inMemoryConcept.removeProperty(propertyName, null);
                }
            }
        }
    }
}
