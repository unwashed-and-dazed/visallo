package org.visallo.core.model.ontology;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.google.inject.Inject;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.io.ReaderDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.vertexium.*;
import org.vertexium.property.StreamingPropertyValue;
import org.vertexium.query.Contains;
import org.vertexium.query.GraphQuery;
import org.vertexium.query.Query;
import org.vertexium.util.CloseableUtils;
import org.vertexium.util.ConvertingIterable;
import org.vertexium.util.IterableUtils;
import org.visallo.core.bootstrap.InjectHelper;
import org.visallo.core.cache.CacheOptions;
import org.visallo.core.cache.CacheService;
import org.visallo.core.config.Configuration;
import org.visallo.core.exception.VisalloAccessDeniedException;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.thumbnails.ThumbnailOntology;
import org.visallo.core.model.lock.LockRepository;
import org.visallo.core.model.longRunningProcess.LongRunningProcessProperties;
import org.visallo.core.model.notification.NotificationOntology;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.properties.types.VisalloProperty;
import org.visallo.core.model.properties.types.VisalloPropertyBase;
import org.visallo.core.model.search.SearchProperties;
import org.visallo.core.model.termMention.TermMentionRepository;
import org.visallo.core.model.user.PrivilegeRepository;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.model.workspace.WorkspaceUser;
import org.visallo.core.ping.PingOntology;
import org.visallo.core.user.SystemUser;
import org.visallo.core.user.User;
import org.visallo.core.util.ExecutorServiceUtil;
import org.visallo.core.util.OWLOntologyUtil;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.clientapi.model.*;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.visallo.core.util.StreamUtil.stream;

public abstract class OntologyRepositoryBase implements OntologyRepository {
    public static final String BASE_OWL_IRI = "http://visallo.org";
    public static final String COMMENT_OWL_IRI = "http://visallo.org/comment";
    public static final String RESOURCE_ENTITY_PNG = "entity.png";
    public static final String TOP_OBJECT_PROPERTY_IRI = "http://www.w3.org/2002/07/owl#topObjectProperty";
    public static final int MAX_DISPLAY_NAME = 50;
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(OntologyRepositoryBase.class);
    private static final String ONTOLOGY_CACHE_NAME = OntologyRepository.class.getName() + ".ontology";
    private static final String CONFIG_ONTOLOGY_CACHE_MAX_SIZE = OntologyRepository.class.getName() + "ontologyCache.maxSize";
    private static final long CONFIG_ONTOLOGY_CACHE_MAX_SIZE_DEFAULT = 100L;
    private final Configuration configuration;
    private final LockRepository lockRepository;
    private final CacheService cacheService;
    private final CacheOptions ontologyCacheOptions;
    private WorkspaceRepository workspaceRepository;
    private PrivilegeRepository privilegeRepository;

    @Inject
    protected OntologyRepositoryBase(
            Configuration configuration,
            LockRepository lockRepository,
            CacheService cacheService
    ) {
        this.configuration = configuration;
        this.lockRepository = lockRepository;
        this.cacheService = cacheService;
        this.ontologyCacheOptions = new CacheOptions()
                .setMaximumSize(configuration.getLong(CONFIG_ONTOLOGY_CACHE_MAX_SIZE, CONFIG_ONTOLOGY_CACHE_MAX_SIZE_DEFAULT));
    }

    public void loadOntologies(Configuration config, Authorizations authorizations) throws Exception {
        lockRepository.lock("ontology", () -> {
            Concept rootConcept = internalGetOrCreateConcept(null, ROOT_CONCEPT_IRI, "root", null, null, null, false, getSystemUser(), PUBLIC);
            Concept entityConcept = internalGetOrCreateConcept(rootConcept, ENTITY_CONCEPT_IRI, "thing", null, null, null, false, getSystemUser(), PUBLIC);
            getOrCreateTopObjectPropertyRelationship(authorizations);

            clearCache();
            addEntityGlyphIcon(entityConcept, authorizations);

            importResourceOwl(OntologyRepositoryBase.class, "base.owl", BASE_OWL_IRI, authorizations);
            importResourceOwl(OntologyRepositoryBase.class, "user.owl", UserRepository.OWL_IRI, authorizations);
            importResourceOwl(OntologyRepositoryBase.class, "termMention.owl", TermMentionRepository.OWL_IRI, authorizations);
            importResourceOwl(OntologyRepositoryBase.class, "workspace.owl", WorkspaceRepository.OWL_IRI, authorizations);
            importResourceOwl(OntologyRepositoryBase.class, "comment.owl", COMMENT_OWL_IRI, authorizations);
            importResourceOwl(OntologyRepositoryBase.class, "search.owl", SearchProperties.IRI, authorizations);
            importResourceOwl(OntologyRepositoryBase.class, "longRunningProcess.owl", LongRunningProcessProperties.OWL_IRI, authorizations);
            importResourceOwl(OntologyRepositoryBase.class, "ping.owl", PingOntology.BASE_IRI, authorizations);
            importResourceOwl(OntologyRepositoryBase.class, "notification.owl", NotificationOntology.IRI, authorizations);
            importResourceOwl(OntologyRepositoryBase.class, "thumbnail.owl", ThumbnailOntology.IRI, authorizations);

            for (Map.Entry<String, Map<String, String>> owlGroup : config.getMultiValue(Configuration.ONTOLOGY_REPOSITORY_OWL).entrySet()) {
                String iri = owlGroup.getValue().get("iri");
                String dir = owlGroup.getValue().get("dir");
                String file = owlGroup.getValue().get("file");

                if (iri == null) {
                    throw new VisalloException("iri is required for group " + Configuration.ONTOLOGY_REPOSITORY_OWL + "." + owlGroup.getKey());
                }
                if (dir == null && file == null) {
                    throw new VisalloException("dir or file is required for " + Configuration.ONTOLOGY_REPOSITORY_OWL + "." + owlGroup.getKey());
                }
                if (dir != null && file != null) {
                    throw new VisalloException("you cannot specify both dir and file for " + Configuration.ONTOLOGY_REPOSITORY_OWL + "." + owlGroup.getKey());
                }

                if (dir != null) {
                    File owlFile = findOwlFile(new File(dir));
                    if (owlFile == null) {
                        throw new VisalloResourceNotFoundException(
                                "could not find owl file in directory " + new File(dir).getAbsolutePath()
                        );
                    }
                    importFile(owlFile, IRI.create(iri), authorizations);
                } else {
                    writePackage(new File(file), IRI.create(iri), authorizations);
                }
            }
            return true;
        });
    }

    private Relationship getOrCreateTopObjectPropertyRelationship(Authorizations authorizations) {
        User user = getSystemUser();
        Relationship topObjectProperty = internalGetOrCreateRelationshipType(
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                TOP_OBJECT_PROPERTY_IRI,
                null,
                false,
                user,
                PUBLIC
        );
        if (topObjectProperty.getUserVisible()) {
            topObjectProperty.setProperty(OntologyProperties.USER_VISIBLE.getPropertyName(), false, user, authorizations);
        }
        return topObjectProperty;
    }

    @Override
    public void importResourceOwl(Class baseClass, String fileName, String iri, Authorizations authorizations) {
        LOGGER.debug("importResourceOwl %s (iri: %s)", fileName, iri);
        InputStream owlFileIn = baseClass.getResourceAsStream(fileName);
        checkNotNull(owlFileIn, "Could not load resource " + baseClass.getResource(fileName) + " [" + fileName + "]");

        try {
            IRI documentIRI = IRI.create(iri);
            byte[] inFileData = IOUtils.toByteArray(owlFileIn);
            try {
                importFileData(inFileData, documentIRI, null, authorizations);
            } catch (OWLOntologyAlreadyExistsException ex) {
                LOGGER.warn("Ontology was already defined but not stored: " + fileName + " (iri: " + iri + ")", ex);
                storeOntologyFile(new ByteArrayInputStream(inFileData), documentIRI, authorizations);
            }
        } catch (Exception ex) {
            throw new VisalloException("Could not import ontology file: " + fileName + " (iri: " + iri + ")", ex);
        } finally {
            CloseableUtils.closeQuietly(owlFileIn);
        }
    }

    private void addEntityGlyphIcon(Concept entityConcept, Authorizations authorizations) {
        if (entityConcept.getGlyphIcon() != null) {
            LOGGER.debug("entityConcept GlyphIcon already set. skipping addEntityGlyphIcon.");
            return;
        }
        LOGGER.debug("addEntityGlyphIcon");
        InputStream entityGlyphIconInputStream = OntologyRepositoryBase.class.getResourceAsStream(RESOURCE_ENTITY_PNG);
        checkNotNull(entityGlyphIconInputStream, "Could not load resource " + RESOURCE_ENTITY_PNG);

        try {
            ByteArrayOutputStream imgOut = new ByteArrayOutputStream();
            IOUtils.copy(entityGlyphIconInputStream, imgOut);

            byte[] rawImg = imgOut.toByteArray();

            addEntityGlyphIconToEntityConcept(entityConcept, rawImg, authorizations);
        } catch (IOException e) {
            throw new VisalloException("invalid stream for glyph icon");
        }
    }

    protected abstract void addEntityGlyphIconToEntityConcept(Concept entityConcept, byte[] rawImg, Authorizations authorizations);

    @Override
    public String guessDocumentIRIFromPackage(File file) throws IOException, ZipException {
        ZipFile zipped = new ZipFile(file);
        if (zipped.isValidZipFile()) {
            File tempDir = Files.createTempDir();
            try {
                LOGGER.info("Extracting: %s to %s", file.getAbsoluteFile(), tempDir.getAbsolutePath());
                zipped.extractAll(tempDir.getAbsolutePath());

                File owlFile = findOwlFile(tempDir);
                return guessDocumentIRIFromFile(owlFile);
            } finally {
                FileUtils.deleteDirectory(tempDir);
            }
        } else {
            if (file.isDirectory()) {
                file = findOwlFile(file);
            }
            return guessDocumentIRIFromFile(file);
        }
    }

    private String guessDocumentIRIFromFile(File owlFile) throws IOException {
        try (FileInputStream owlFileIn = new FileInputStream(owlFile)) {
            String owlContents = IOUtils.toString(owlFileIn);

            Pattern iriRegex = Pattern.compile("<owl:Ontology rdf:about=\"(.*?)\">");
            Matcher m = iriRegex.matcher(owlContents);
            if (m.find()) {
                return m.group(1);
            }
            return null;
        }
    }

    @Override
    public void importFile(File inFile, IRI documentIRI, Authorizations authorizations) throws Exception {
        checkNotNull(inFile, "inFile cannot be null");
        if (!inFile.exists()) {
            throw new VisalloException("File " + inFile + " does not exist");
        }
        File inDir = inFile.getParentFile();

        try (FileInputStream inFileIn = new FileInputStream(inFile)) {
            LOGGER.debug("importing %s", inFile.getAbsolutePath());
            byte[] inFileData = IOUtils.toByteArray(inFileIn);
            importFileData(inFileData, documentIRI, inDir, authorizations);
        }
    }

    @Override
    public void importFileData(
            byte[] inFileData,
            IRI documentIRI,
            File inDir,
            Authorizations authorizations
    ) throws Exception {
        if (!hasFileChanged(documentIRI, inFileData)) {
            LOGGER.info("skipping %s, file has not changed", documentIRI);
            return;
        }
        Reader inFileReader = new InputStreamReader(new ByteArrayInputStream(inFileData));

        OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();
        OWLOntologyManager m = createOwlOntologyManager(config, documentIRI);

        OWLOntologyDocumentSource documentSource = new ReaderDocumentSource(inFileReader, documentIRI);
        OWLOntology o = m.loadOntologyFromOntologyDocument(documentSource, config);

        long totalStartTime = System.currentTimeMillis();

        long startTime = System.currentTimeMillis();
        importOntologyAnnotationProperties(o, inDir, authorizations);
        clearCache(); // this is required to cause a new lookup of classes for data and object properties.
        long endTime = System.currentTimeMillis();
        long importAnnotationPropertiesTime = endTime - startTime;

        startTime = System.currentTimeMillis();
        importOntologyClasses(o, inDir, authorizations);
        clearCache(); // this is required to cause a new lookup of classes for data and object properties.
        endTime = System.currentTimeMillis();
        long importConceptsTime = endTime - startTime;

        startTime = System.currentTimeMillis();
        importObjectProperties(o, authorizations);
        clearCache(); // needed to find the relationship for inverse of
        endTime = System.currentTimeMillis();
        long importObjectPropertiesTime = endTime - startTime;

        startTime = System.currentTimeMillis();
        importInverseOfObjectProperties(o);
        endTime = System.currentTimeMillis();
        long importInverseOfObjectPropertiesTime = endTime - startTime;
        long totalEndTime = System.currentTimeMillis();

        startTime = System.currentTimeMillis();
        importDataProperties(o, authorizations);
        endTime = System.currentTimeMillis();
        long importDataPropertiesTime = endTime - startTime;

        LOGGER.debug("import annotation properties time: %dms", importAnnotationPropertiesTime);
        LOGGER.debug("import concepts time: %dms", importConceptsTime);
        LOGGER.debug("import data properties time: %dms", importDataPropertiesTime);
        LOGGER.debug("import object properties time: %dms", importObjectPropertiesTime);
        LOGGER.debug("import inverse of object properties time: %dms", importInverseOfObjectPropertiesTime);
        LOGGER.debug("import total time: %dms", totalEndTime - totalStartTime);

        // do this last after everything was successful so that isOntologyDefined can be used
        storeOntologyFile(new ByteArrayInputStream(inFileData), documentIRI, authorizations);

        clearCache();
    }

    protected boolean hasFileChanged(IRI documentIRI, byte[] inFileData) {
        return true;
    }

    private void importInverseOfObjectProperties(OWLOntology o) {
        for (OWLObjectProperty objectProperty : o.getObjectPropertiesInSignature()) {
            if (!o.isDeclared(objectProperty, Imports.EXCLUDED)) {
                continue;
            }
            importInverseOf(o, objectProperty);
        }
    }

    private void importObjectProperties(OWLOntology o, Authorizations authorizations) {
        for (OWLObjectProperty objectProperty : o.getObjectPropertiesInSignature()) {
            importObjectProperty(o, objectProperty, authorizations);
        }
    }

    private void importDataProperties(OWLOntology o, Authorizations authorizations) {
        // find all extended data tables and pre-create them
        boolean foundExtendedDataTable = false;
        for (OWLDataProperty dataTypeProperty : o.getDataPropertiesInSignature()) {
            for (OWLDataRange rangeClassExpr : EntitySearcher.getRanges(dataTypeProperty, o)) {
                String rangeIri = ((HasIRI) rangeClassExpr).getIRI().toString();
                if (OWLOntologyUtil.EXTENDED_DATA_TABLE_IRI.equals(rangeIri)) {
                    importDataProperty(o, dataTypeProperty, authorizations);
                    foundExtendedDataTable = true;
                    break;
                }
            }
        }
        if (foundExtendedDataTable) {
            clearCache();
        }
        for (OWLDataProperty dataTypeProperty : o.getDataPropertiesInSignature()) {
            importDataProperty(o, dataTypeProperty, authorizations);
        }
    }

    private void importOntologyAnnotationProperties(OWLOntology o, File inDir, Authorizations authorizations) {
        for (OWLAnnotationProperty annotation : o.getAnnotationPropertiesInSignature()) {
            importOntologyAnnotationProperty(o, annotation, inDir, authorizations);
        }
    }

    protected void importOntologyAnnotationProperty(
            OWLOntology o,
            OWLAnnotationProperty annotationProperty,
            File inDir,
            Authorizations authorizations
    ) {

    }

    @Deprecated
    @Override
    public final void updatePropertyDependentIris(OntologyProperty property, Collection<String> dependentPropertyIris) {
        updatePropertyDependentIris(property, dependentPropertyIris, null, PUBLIC);
    }

    @Deprecated
    @Override
    public final void updatePropertyDomainIris(OntologyProperty property, Set<String> domainIris) {
        updatePropertyDomainIris(property, domainIris, null, PUBLIC);
    }

    private void importOntologyClasses(OWLOntology o, File inDir, Authorizations authorizations) throws IOException {
        for (OWLClass ontologyClass : o.getClassesInSignature()) {
            importOntologyClass(o, ontologyClass, inDir, authorizations);
        }
    }

    public OWLOntologyManager createOwlOntologyManager(
            OWLOntologyLoaderConfiguration config,
            IRI excludeDocumentIRI
    ) throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
        loadOntologyFiles(m, config, excludeDocumentIRI);
        return m;
    }

    protected abstract void storeOntologyFile(InputStream inputStream, IRI documentIRI, Authorizations authorizations);

    protected abstract List<OWLOntology> loadOntologyFiles(
            OWLOntologyManager m,
            OWLOntologyLoaderConfiguration config,
            IRI excludedIRI
    ) throws Exception;

    protected Concept importOntologyClass(
            OWLOntology o,
            OWLClass ontologyClass,
            File inDir,
            Authorizations authorizations
    ) throws IOException {
        String uri = ontologyClass.getIRI().toString();
        if ("http://www.w3.org/2002/07/owl#Thing".equals(uri)) {
            return getEntityConcept(OntologyRepository.PUBLIC);
        }

        String label = OWLOntologyUtil.getLabel(o, ontologyClass);
        checkNotNull(label, "label cannot be null or empty: " + uri);
        LOGGER.info("Importing ontology class " + uri + " (label: " + label + ")");

        boolean isDeclaredInOntology = o.isDeclared(ontologyClass);

        Concept parent = getParentConcept(o, ontologyClass, inDir, authorizations);
        Concept result = internalGetOrCreateConcept(parent, uri, label, null, null, inDir, isDeclaredInOntology, getSystemUser(), PUBLIC);

        User user = getSystemUser();

        for (OWLAnnotation annotation : EntitySearcher.getAnnotations(ontologyClass, o)) {
            String annotationIri = annotation.getProperty().getIRI().toString();
            OWLLiteral valueLiteral = (OWLLiteral) annotation.getValue();
            String valueString = valueLiteral.getLiteral();

            if (annotationIri.equals(OntologyProperties.INTENT.getPropertyName())) {
                result.addIntent(valueString, user, authorizations);
                continue;
            }

            if (annotationIri.equals(OntologyProperties.SEARCHABLE.getPropertyName())) {
                boolean searchable = Boolean.parseBoolean(valueString);
                result.setProperty(OntologyProperties.SEARCHABLE.getPropertyName(), searchable, user, authorizations);
                continue;
            }

            if (annotationIri.equals(OntologyProperties.SORTABLE.getPropertyName())) {
                boolean sortable = Boolean.parseBoolean(valueString);
                result.setProperty(OntologyProperties.SORTABLE.getPropertyName(), sortable, user, authorizations);
                continue;
            }

            if (annotationIri.equals(OntologyProperties.SORT_PRIORITY.getPropertyName())) {
                if (valueString.trim().length() == 0) {
                    continue;
                }
                Integer sortPriority = Integer.parseInt(valueString);
                result.setProperty(OntologyProperties.SORT_PRIORITY.getPropertyName(), sortPriority, user, authorizations);
                continue;
            }

            if (annotationIri.equals(OntologyProperties.ADDABLE.getPropertyName())) {
                boolean searchable = Boolean.parseBoolean(valueString);
                result.setProperty(OntologyProperties.ADDABLE.getPropertyName(), searchable, user, authorizations);
                continue;
            }

            if (annotationIri.equals(OntologyProperties.USER_VISIBLE.getPropertyName())) {
                boolean userVisible = Boolean.parseBoolean(valueString);
                result.setProperty(OntologyProperties.USER_VISIBLE.getPropertyName(), userVisible, user, authorizations);
                continue;
            }

            if (annotationIri.equals(OntologyProperties.GLYPH_ICON_FILE_NAME.getPropertyName())) {
                setIconProperty(
                        result,
                        inDir,
                        valueString,
                        OntologyProperties.GLYPH_ICON.getPropertyName(),
                        user,
                        authorizations
                );
                continue;
            }

            if (annotationIri.equals(OntologyProperties.GLYPH_ICON_SELECTED_FILE_NAME.getPropertyName())) {
                setIconProperty(
                        result,
                        inDir,
                        valueString,
                        OntologyProperties.GLYPH_ICON_SELECTED.getPropertyName(),
                        user,
                        authorizations
                );
                continue;
            }

            if (annotationIri.equals(OntologyProperties.MAP_GLYPH_ICON_FILE_NAME.getPropertyName())) {
                setIconProperty(
                        result,
                        inDir,
                        valueString,
                        OntologyProperties.MAP_GLYPH_ICON.getPropertyName(),
                        user,
                        authorizations
                );
                continue;
            }

            if (annotationIri.equals(OntologyProperties.ADD_RELATED_CONCEPT_WHITE_LIST.getPropertyName())) {
                if (valueString.trim().length() == 0) {
                    continue;
                }
                result.setProperty(
                        OntologyProperties.ADD_RELATED_CONCEPT_WHITE_LIST.getPropertyName(),
                        valueString.trim(),
                        user,
                        authorizations
                );
                continue;
            }

            if (annotationIri.equals("http://www.w3.org/2000/01/rdf-schema#label")) {
                result.setProperty(OntologyProperties.DISPLAY_NAME.getPropertyName(), valueString, user, authorizations);
                continue;
            }

            if (annotationIri.equals(OntologyProperties.UPDATEABLE.getPropertyName())) {
                boolean updateable = Boolean.parseBoolean(valueString);
                result.setProperty(OntologyProperties.UPDATEABLE.getPropertyName(), updateable, user, authorizations);
                continue;
            }

            if (annotationIri.equals(OntologyProperties.DELETEABLE.getPropertyName())) {
                boolean deleteable = Boolean.parseBoolean(valueString);
                result.setProperty(OntologyProperties.DELETEABLE.getPropertyName(), deleteable, user, authorizations);
                continue;
            }

            result.setProperty(annotationIri, valueString, user, authorizations);
        }

        return result;
    }

    protected void setIconProperty(
            Concept concept,
            File inDir,
            String glyphIconFileName,
            String propertyKey,
            User user,
            Authorizations authorizations
    ) throws IOException {
        if (glyphIconFileName != null) {
            File iconFile = new File(inDir, glyphIconFileName);
            if (!iconFile.exists()) {
                throw new RuntimeException("Could not find icon file: " + iconFile.toString());
            }
            try (InputStream iconFileIn = new FileInputStream(iconFile)) {
                StreamingPropertyValue value = new StreamingPropertyValue(iconFileIn, byte[].class);
                value.searchIndex(false);
                value.store(true);
                concept.setProperty(propertyKey, value, user, authorizations);
            }
        }
    }

    protected Concept getParentConcept(
            OWLOntology o,
            OWLClass ontologyClass,
            File inDir,
            Authorizations authorizations
    ) throws IOException {
        Collection<OWLClassExpression> superClasses = EntitySearcher.getSuperClasses(ontologyClass, o);
        if (superClasses.size() == 0) {
            return getEntityConcept(OntologyRepository.PUBLIC);
        } else if (superClasses.size() == 1) {
            OWLClassExpression superClassExpr = superClasses.iterator().next();
            OWLClass superClass = superClassExpr.asOWLClass();
            String superClassUri = superClass.getIRI().toString();
            Concept parent = getConceptByIRI(superClassUri, PUBLIC);
            if (parent != null) {
                return parent;
            }

            parent = importOntologyClass(o, superClass, inDir, authorizations);
            if (parent == null) {
                throw new VisalloException("Could not find or create parent: " + superClass);
            }
            return parent;
        } else {
            throw new VisalloException("Unhandled multiple super classes. Found " + superClasses.size() + ", expected 0 or 1.");
        }
    }

    protected void importDataProperty(OWLOntology o, OWLDataProperty dataTypeProperty, Authorizations authorizations) {
        String propertyIRI = dataTypeProperty.getIRI().toString();
        try {
            String propertyDisplayName = OWLOntologyUtil.getLabel(o, dataTypeProperty);
            PropertyType propertyType = OWLOntologyUtil.getPropertyType(o, dataTypeProperty);
            checkNotNull(propertyType, "Failed to decode property type of: " + propertyIRI);
            boolean userVisible = OWLOntologyUtil.getUserVisible(o, dataTypeProperty);
            boolean searchable = OWLOntologyUtil.getSearchable(o, dataTypeProperty);
            boolean addable = OWLOntologyUtil.getAddable(o, dataTypeProperty);
            boolean sortable = !propertyType.equals(PropertyType.GEO_LOCATION) && !propertyType.equals(PropertyType.GEO_SHAPE) && OWLOntologyUtil.getSortable(o, dataTypeProperty);
            Integer sortPriority = OWLOntologyUtil.getSortPriority(o, dataTypeProperty);
            String displayType = OWLOntologyUtil.getDisplayType(o, dataTypeProperty);
            String propertyGroup = OWLOntologyUtil.getPropertyGroup(o, dataTypeProperty);
            String validationFormula = OWLOntologyUtil.getValidationFormula(o, dataTypeProperty);
            String displayFormula = OWLOntologyUtil.getDisplayFormula(o, dataTypeProperty);
            ImmutableList<String> dependentPropertyIris = OWLOntologyUtil.getDependentPropertyIris(o, dataTypeProperty);
            Double boost = OWLOntologyUtil.getBoost(o, dataTypeProperty);
            String[] intents = OWLOntologyUtil.getIntents(o, dataTypeProperty);
            boolean deleteable = OWLOntologyUtil.getDeleteable(o, dataTypeProperty);
            boolean updateable = OWLOntologyUtil.getUpdateable(o, dataTypeProperty);

            User user = getSystemUser();

            List<Concept> domainConcepts = new ArrayList<>();
            for (OWLClassExpression domainClassExpr : EntitySearcher.getDomains(dataTypeProperty, o)) {
                OWLClass domainClass = domainClassExpr.asOWLClass();
                String domainClassIri = domainClass.getIRI().toString();
                Concept domainConcept = getConceptByIRI(domainClassIri, PUBLIC);
                if (domainConcept == null) {
                    LOGGER.error("Could not find class with IRI \"%s\" for data type property \"%s\"", domainClassIri, dataTypeProperty.getIRI());
                } else {
                    LOGGER.info("Adding data property " + propertyIRI + " to class " + domainConcept.getIRI());
                    domainConcepts.add(domainConcept);
                }
            }

            List<Relationship> domainRelationships = new ArrayList<>();
            for (OWLAnnotation domainAnnotation : OWLOntologyUtil.getObjectPropertyDomains(o, dataTypeProperty)) {
                String domainClassIri = OWLOntologyUtil.getOWLAnnotationValueAsString(domainAnnotation);
                Relationship domainRelationship = getRelationshipByIRI(domainClassIri, PUBLIC);
                if (domainRelationship == null) {
                    LOGGER.error("Could not find relationship with IRI \"%s\" for data type property \"%s\"", domainClassIri, dataTypeProperty.getIRI());
                } else {
                    LOGGER.info("Adding data property " + propertyIRI + " to relationship " + domainRelationship.getIRI());
                    domainRelationships.add(domainRelationship);
                }
            }

            List<String> extendedDataTableNames = OWLOntologyUtil.getExtendedDataTableNames(o, dataTypeProperty);
            Map<String, String> possibleValues = OWLOntologyUtil.getPossibleValues(o, dataTypeProperty);
            Collection<TextIndexHint> textIndexHints = OWLOntologyUtil.getTextIndexHints(o, dataTypeProperty);
            OntologyProperty property = addPropertyTo(
                    domainConcepts,
                    domainRelationships,
                    extendedDataTableNames,
                    propertyIRI,
                    propertyDisplayName,
                    propertyType,
                    possibleValues,
                    textIndexHints,
                    userVisible,
                    searchable,
                    addable,
                    sortable,
                    sortPriority,
                    displayType,
                    propertyGroup,
                    boost,
                    validationFormula,
                    displayFormula,
                    dependentPropertyIris,
                    intents,
                    deleteable,
                    updateable,
                    user,
                    PUBLIC
            );
            property.setProperty(OntologyProperties.DISPLAY_NAME.getPropertyName(), propertyDisplayName, user, authorizations);

            for (OWLAnnotation annotation : EntitySearcher.getAnnotations(dataTypeProperty, o)) {
                String annotationIri = annotation.getProperty().getIRI().toString();
                String valueString = annotation.getValue() instanceof OWLLiteral
                        ? ((OWLLiteral) annotation.getValue()).getLiteral()
                        : annotation.getValue().toString();

                if (annotationIri.equals(OntologyProperties.TITLE_FORMULA.getPropertyName())) {
                    property.setProperty(
                            OntologyProperties.TITLE_FORMULA.getPropertyName(),
                            valueString,
                            user,
                            authorizations
                    );
                    continue;
                }

                if (annotationIri.equals(OntologyProperties.SUBTITLE_FORMULA.getPropertyName())) {
                    property.setProperty(
                            OntologyProperties.SUBTITLE_FORMULA.getPropertyName(),
                            valueString,
                            user,
                            authorizations
                    );
                    continue;
                }

                if (annotationIri.equals(OntologyProperties.TIME_FORMULA.getPropertyName())) {
                    property.setProperty(
                            OntologyProperties.TIME_FORMULA.getPropertyName(),
                            valueString,
                            user,
                            authorizations
                    );
                    continue;
                }
            }
        } catch (Throwable ex) {
            throw new VisalloException("Failed to load data property: " + propertyIRI, ex);
        }
    }

    @Deprecated
    @Override
    public final OntologyProperty getOrCreateProperty(OntologyPropertyDefinition ontologyPropertyDefinition) {
        return getOrCreateProperty(ontologyPropertyDefinition, null, PUBLIC);
    }

    @Override
    public final OntologyProperty getOrCreateProperty(OntologyPropertyDefinition ontologyPropertyDefinition, User user, String workspaceId) {
        checkPrivileges(user, workspaceId);

        OntologyProperty property = getPropertyByIRI(ontologyPropertyDefinition.getPropertyIri(), workspaceId);
        if (property != null) {
            return property;
        }
        return addPropertyTo(
                ontologyPropertyDefinition.getConcepts(),
                ontologyPropertyDefinition.getRelationships(),
                ontologyPropertyDefinition.getExtendedDataTableNames(),
                ontologyPropertyDefinition.getPropertyIri(),
                ontologyPropertyDefinition.getDisplayName(),
                ontologyPropertyDefinition.getDataType(),
                ontologyPropertyDefinition.getPossibleValues(),
                ontologyPropertyDefinition.getTextIndexHints(),
                ontologyPropertyDefinition.isUserVisible(),
                ontologyPropertyDefinition.isSearchable(),
                ontologyPropertyDefinition.isAddable(),
                ontologyPropertyDefinition.isSortable(),
                ontologyPropertyDefinition.getSortPriority(),
                ontologyPropertyDefinition.getDisplayType(),
                ontologyPropertyDefinition.getPropertyGroup(),
                ontologyPropertyDefinition.getBoost(),
                ontologyPropertyDefinition.getValidationFormula(),
                ontologyPropertyDefinition.getDisplayFormula(),
                ontologyPropertyDefinition.getDependentPropertyIris(),
                ontologyPropertyDefinition.getIntents(),
                ontologyPropertyDefinition.getDeleteable(),
                ontologyPropertyDefinition.getUpdateable(),
                user,
                workspaceId
        );
    }

    protected abstract OntologyProperty addPropertyTo(
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
    );

    protected Relationship importObjectProperty(
            OWLOntology o,
            OWLObjectProperty objectProperty,
            Authorizations authorizations
    ) {
        String iri = objectProperty.getIRI().toString();
        String label = OWLOntologyUtil.getLabel(o, objectProperty);

        checkNotNull(label, "label cannot be null or empty for " + iri);
        LOGGER.info("Importing ontology object property " + iri + " (label: " + label + ")");

        boolean isDeclaredInOntology = o.isDeclared(objectProperty);

        User user = getSystemUser();
        Relationship parent = getParentObjectProperty(o, objectProperty, authorizations);
        Relationship relationship = internalGetOrCreateRelationshipType(
                parent,
                getDomainsConcepts(o, objectProperty),
                getRangesConcepts(o, objectProperty),
                iri,
                null,
                isDeclaredInOntology,
                user,
                PUBLIC
        );

        for (OWLAnnotation annotation : EntitySearcher.getAnnotations(objectProperty, o)) {
            String annotationIri = annotation.getProperty().getIRI().toString();
            OWLLiteral valueLiteral = (OWLLiteral) annotation.getValue();
            String valueString = valueLiteral.getLiteral();

            if (annotationIri.equals(OntologyProperties.INTENT.getPropertyName())) {
                relationship.addIntent(valueString, user, authorizations);
                continue;
            }

            if (annotationIri.equals(OntologyProperties.USER_VISIBLE.getPropertyName())) {
                relationship.setProperty(
                        OntologyProperties.USER_VISIBLE.getPropertyName(),
                        Boolean.parseBoolean(valueString),
                        user,
                        authorizations
                );
                continue;
            }

            if (annotationIri.equals(OntologyProperties.DELETEABLE.getPropertyName())) {
                relationship.setProperty(
                        OntologyProperties.DELETEABLE.getPropertyName(),
                        Boolean.parseBoolean(valueString),
                        user,
                        authorizations
                );
                continue;
            }

            if (annotationIri.equals(OntologyProperties.UPDATEABLE.getPropertyName())) {
                relationship.setProperty(
                        OntologyProperties.UPDATEABLE.getPropertyName(),
                        Boolean.parseBoolean(valueString),
                        user,
                        authorizations
                );
                continue;
            }

            if (annotationIri.equals("http://www.w3.org/2000/01/rdf-schema#label")) {
                relationship.setProperty(
                        OntologyProperties.DISPLAY_NAME.getPropertyName(),
                        valueString,
                        user,
                        authorizations
                );
                continue;
            }

            relationship.setProperty(annotationIri, valueString, user, authorizations);
        }
        return relationship;
    }

    private Relationship getParentObjectProperty(
            OWLOntology o,
            OWLObjectProperty objectProperty,
            Authorizations authorizations
    ) {
        Collection<OWLObjectPropertyExpression> superProperties = EntitySearcher.getSuperProperties(objectProperty, o);
        if (superProperties.size() == 0) {
            return getOrCreateTopObjectPropertyRelationship(authorizations);
        } else if (superProperties.size() == 1) {
            OWLObjectPropertyExpression superPropertyExpr = superProperties.iterator().next();
            OWLObjectProperty superProperty = superPropertyExpr.asOWLObjectProperty();
            String superPropertyUri = superProperty.getIRI().toString();
            Relationship parent = getRelationshipByIRI(superPropertyUri, PUBLIC);
            if (parent != null) {
                return parent;
            }

            parent = importObjectProperty(o, superProperty, authorizations);
            if (parent == null) {
                throw new VisalloException("Could not find or create parent: " + superProperty);
            }
            return parent;
        } else {
            throw new VisalloException("Unhandled multiple super properties. Found " + superProperties.size() + ", expected 0 or 1.");
        }
    }

    protected void importInverseOf(OWLOntology o, OWLObjectProperty objectProperty) {
        String iri = objectProperty.getIRI().toString();
        Relationship fromRelationship = null;

        for (OWLObjectPropertyExpression inverseOf : EntitySearcher.getInverses(objectProperty, o)) {
            if (inverseOf instanceof OWLObjectProperty) {
                if (fromRelationship == null) {
                    fromRelationship = getRelationshipByIRI(iri, PUBLIC);
                    checkNotNull(fromRelationship, "could not find from relationship: " + iri);
                }

                OWLObjectProperty inverseOfOWLObjectProperty = (OWLObjectProperty) inverseOf;
                String inverseOfIri = inverseOfOWLObjectProperty.getIRI().toString();
                Relationship inverseOfRelationship = getRelationshipByIRI(inverseOfIri, PUBLIC);
                getOrCreateInverseOfRelationship(fromRelationship, inverseOfRelationship);
            }
        }
    }

    protected abstract void getOrCreateInverseOfRelationship(
            Relationship fromRelationship,
            Relationship inverseOfRelationship
    );

    private Iterable<Concept> getRangesConcepts(OWLOntology o, OWLObjectProperty objectProperty) {
        List<Concept> ranges = new ArrayList<>();
        for (OWLClassExpression rangeClassExpr : EntitySearcher.getRanges(objectProperty, o)) {
            OWLClass rangeClass = rangeClassExpr.asOWLClass();
            String rangeClassIri = rangeClass.getIRI().toString();
            Concept ontologyClass = getConceptByIRI(rangeClassIri, PUBLIC);
            if (ontologyClass == null) {
                LOGGER.error("Could not find class with IRI \"%s\" for object property \"%s\"", rangeClassIri, objectProperty.getIRI());
            } else {
                ranges.add(ontologyClass);
            }
        }
        return ranges;
    }

    private Iterable<Concept> getDomainsConcepts(OWLOntology o, OWLObjectProperty objectProperty) {
        List<Concept> domains = new ArrayList<>();
        for (OWLClassExpression domainClassExpr : EntitySearcher.getDomains(objectProperty, o)) {
            OWLClass rangeClass = domainClassExpr.asOWLClass();
            String rangeClassIri = rangeClass.getIRI().toString();
            Concept ontologyClass = getConceptByIRI(rangeClassIri, PUBLIC);
            if (ontologyClass == null) {
                LOGGER.error("Could not find class with IRI \"%s\" for object property \"%s\"", rangeClassIri, objectProperty.getIRI());
            } else {
                domains.add(ontologyClass);
            }
        }
        return domains;
    }

    @Override
    public void writePackage(File file, IRI documentIRI, Authorizations authorizations) throws Exception {
        if (!file.exists()) {
            throw new VisalloException("OWL file does not exist: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            throw new VisalloException("OWL file is not a file: " + file.getAbsolutePath());
        }
        ZipFile zipped = new ZipFile(file);
        if (zipped.isValidZipFile()) {
            File tempDir = Files.createTempDir();
            try {
                LOGGER.info("Extracting: %s to %s", file.getAbsoluteFile(), tempDir.getAbsolutePath());
                zipped.extractAll(tempDir.getAbsolutePath());

                File owlFile = findOwlFile(tempDir);
                importFile(owlFile, documentIRI, authorizations);
            } finally {
                FileUtils.deleteDirectory(tempDir);
            }
        } else {
            importFile(file, documentIRI, authorizations);
        }
    }

    protected File findOwlFile(File fileOrDir) {
        if (fileOrDir.isFile()) {
            return fileOrDir;
        }
        File[] files = fileOrDir.listFiles();
        if (files == null) {
            return null;
        }
        for (File child : files) {
            if (child.isDirectory()) {
                File found = findOwlFile(child);
                if (found != null) {
                    return found;
                }
            } else if (child.getName().toLowerCase().endsWith(".owl")) {
                return child;
            }
        }
        return null;
    }

    @Deprecated
    @Override
    public final Set<Concept> getConceptAndAllChildrenByIri(String conceptIRI) {
        return getConceptAndAllChildrenByIri(conceptIRI, PUBLIC);
    }

    @Override
    public Set<Concept> getConceptAndAllChildrenByIri(String conceptIRI, String workspaceId) {
        Concept concept = getConceptByIRI(conceptIRI, workspaceId);
        if (concept == null) {
            return null;
        }
        return getConceptAndAllChildren(concept, workspaceId);
    }

    @Deprecated
    @Override
    public final Set<Concept> getConceptAndAllChildren(Concept concept) {
        return getConceptAndAllChildren(concept, PUBLIC);
    }

    @Override
    public Set<Concept> getConceptAndAllChildren(Concept concept, String workspaceId) {
        List<Concept> childConcepts = getChildConcepts(concept, workspaceId);
        Set<Concept> result = Sets.newHashSet(concept);
        if (childConcepts.size() > 0) {
            List<Concept> childrenList = new ArrayList<>();
            for (Concept childConcept : childConcepts) {
                Set<Concept> child = getConceptAndAllChildren(childConcept, workspaceId);
                childrenList.addAll(child);
            }
            result.addAll(childrenList);
        }
        return result;
    }

    @Override
    public Set<Concept> getAncestorConcepts(Concept concept, String workspaceId) {
        Set<Concept> result = Sets.newHashSet();
        Concept parentConcept = getParentConcept(concept, workspaceId);
        while (parentConcept != null) {
            result.add(parentConcept);
            parentConcept = getParentConcept(parentConcept, workspaceId);
        }
        return result;
    }

    @Override
    public Set<Concept> getConceptAndAncestors(Concept concept, String workspaceId) {
        Set<Concept> result = Sets.newHashSet(concept);
        result.addAll(getAncestorConcepts(concept, workspaceId));
        return result;
    }

    protected List<Concept> getChildConcepts(Concept concept) {
        return getChildConcepts(concept, PUBLIC);
    }

    protected abstract List<Concept> getChildConcepts(Concept concept, String workspaceId);

    @Deprecated
    @Override
    public final Set<Relationship> getRelationshipAndAllChildren(Relationship relationship) {
        return getRelationshipAndAllChildren(relationship, PUBLIC);
    }

    @Override
    public Set<Relationship> getRelationshipAndAllChildrenByIRI(String relationshipIRI, String workspaceId) {
        Relationship relationship = getRelationshipByIRI(relationshipIRI, workspaceId);
        return getRelationshipAndAllChildren(relationship, workspaceId);
    }

    @Override
    public Set<Relationship> getRelationshipAndAllChildren(Relationship relationship, String workspaceId) {
        List<Relationship> childRelationships = getChildRelationships(relationship, workspaceId);
        Set<Relationship> result = Sets.newHashSet(relationship);
        if (childRelationships.size() > 0) {
            List<Relationship> childrenList = new ArrayList<>();
            for (Relationship childRelationship : childRelationships) {
                Set<Relationship> child = getRelationshipAndAllChildren(childRelationship, workspaceId);
                childrenList.addAll(child);
            }
            result.addAll(childrenList);
        }
        return result;
    }

    @Override
    public Set<Relationship> getAncestorRelationships(Relationship relationship, String workspaceId) {
        Set<Relationship> result = Sets.newHashSet();
        Relationship parentRelationship = getParentRelationship(relationship, workspaceId);
        while (parentRelationship != null) {
            result.add(parentRelationship);
            parentRelationship = getParentRelationship(parentRelationship, workspaceId);
        }
        return result;
    }

    @Override
    public Set<Relationship> getRelationshipAndAncestors(Relationship relationship, String workspaceId) {
        Set<Relationship> result = Sets.newHashSet(relationship);
        result.addAll(getAncestorRelationships(relationship, workspaceId));
        return result;
    }

    @Deprecated
    @Override
    public final boolean hasRelationshipByIRI(String relationshipIRI) {
        return hasRelationshipByIRI(relationshipIRI, PUBLIC);
    }

    @Override
    public boolean hasRelationshipByIRI(String relationshipIRI, String workspaceId) {
        return getRelationshipByIRI(relationshipIRI, workspaceId) != null;
    }

    protected abstract List<Relationship> getChildRelationships(Relationship relationship, String workspaceId);

    @Deprecated
    @Override
    public final void resolvePropertyIds(JSONArray filterJson) throws JSONException {
        resolvePropertyIds(filterJson, PUBLIC);
    }

    @Override
    public void resolvePropertyIds(JSONArray filterJson, String workspaceId) throws JSONException {
        for (int i = 0; i < filterJson.length(); i++) {
            JSONObject filter = filterJson.getJSONObject(i);
            if (filter.has("propertyId") && !filter.has("propertyName")) {
                String propertyVertexId = filter.getString("propertyId");
                OntologyProperty property = getPropertyByIRI(propertyVertexId, workspaceId);
                if (property == null) {
                    throw new RuntimeException("Could not find property with id: " + propertyVertexId);
                }
                filter.put("propertyName", property.getTitle());
                filter.put("propertyDataType", property.getDataType());
            }
        }
    }

    @Deprecated
    @Override
    public final Concept getConceptByIRI(String conceptIRI) {
        return getConceptByIRI(conceptIRI, PUBLIC);
    }

    @Override
    public Concept getConceptByIRI(String conceptIRI, String workspaceId) {
        return Iterables.getFirst(getConceptsByIRI(Collections.singletonList(conceptIRI), workspaceId), null);
    }

    @Override
    public Iterable<Concept> getConceptsByIRI(List<String> conceptIRIs, String workspaceId) {
        return StreamSupport.stream(getConceptsWithProperties(workspaceId).spliterator(), false)
                .filter(concept -> conceptIRIs.contains(concept.getIRI()))
                .collect(Collectors.toSet());
    }

    @Deprecated
    @Override
    public final OntologyProperty getPropertyByIRI(String propertyIRI) {
        return getPropertyByIRI(propertyIRI, PUBLIC);
    }

    @Override
    public OntologyProperty getPropertyByIRI(String propertyIRI, String workspaceId) {
        return Iterables.getFirst(getPropertiesByIRI(Collections.singletonList(propertyIRI), workspaceId), null);
    }

    @Override
    public Iterable<OntologyProperty> getPropertiesByIRI(List<String> propertyIRIs, String workspaceId) {
        return StreamSupport.stream(getProperties(workspaceId).spliterator(), false)
                .filter(property -> propertyIRIs.contains(property.getIri()))
                .collect(Collectors.toList());
    }

    @Deprecated
    @Override
    public final OntologyProperty getRequiredPropertyByIRI(String propertyIRI) {
        return getRequiredPropertyByIRI(propertyIRI, PUBLIC);
    }

    @Override
    public OntologyProperty getRequiredPropertyByIRI(String propertyIRI, String workspaceId) {
        OntologyProperty property = getPropertyByIRI(propertyIRI, workspaceId);
        if (property == null) {
            throw new VisalloException("Could not find property by IRI: " + propertyIRI);
        }
        return property;
    }

    @Deprecated
    @Override
    public final Iterable<Relationship> getRelationships() {
        return getRelationships(PUBLIC);
    }

    @Deprecated
    @Override
    public final Iterable<OntologyProperty> getProperties() {
        return getProperties(PUBLIC);
    }

    @Deprecated
    @Override
    public final String getDisplayNameForLabel(String relationshipIRI) {
        return getDisplayNameForLabel(relationshipIRI, PUBLIC);
    }

    @Deprecated
    @Override
    public Iterable<Concept> getConceptsWithProperties() {
        return getConceptsWithProperties(PUBLIC);
    }

    @Deprecated
    @Override
    public final Concept getParentConcept(Concept concept) {
        return getParentConcept(concept, PUBLIC);
    }

    @Deprecated
    @Override
    public final Relationship getRelationshipByIRI(String relationshipIRI) {
        return getRelationshipByIRI(relationshipIRI, PUBLIC);
    }

    @Override
    public Relationship getRelationshipByIRI(String relationshipIRI, String workspaceId) {
        return Iterables.getFirst(getRelationshipsByIRI(Collections.singletonList(relationshipIRI), workspaceId), null);
    }

    @Override
    public Iterable<Relationship> getRelationshipsByIRI(List<String> relationshipIRIs, String workspaceId) {
        return StreamSupport.stream(getRelationships(workspaceId).spliterator(), false)
                .filter(relationship -> relationshipIRIs.contains(relationship.getIRI()))
                .collect(Collectors.toList());
    }

    @Deprecated
    @Override
    public final Concept getConceptByIntent(String intent) {
        return getConceptByIntent(intent, PUBLIC);
    }

    @Override
    public Concept getConceptByIntent(String intent, String workspaceId) {
        String configurationKey = CONFIG_INTENT_CONCEPT_PREFIX + intent;
        String conceptIri = getConfiguration().get(configurationKey, null);
        if (conceptIri != null) {
            Concept concept = getConceptByIRI(conceptIri, workspaceId);
            if (concept == null) {
                throw new VisalloException("Could not find concept by configuration key: " + configurationKey);
            }
            return concept;
        }

        List<Concept> concepts = findLoadedConceptsByIntent(intent, workspaceId);
        if (concepts.size() == 0) {
            return null;
        }
        if (concepts.size() == 1) {
            return concepts.get(0);
        }

        String iris = Joiner.on(',').join(new ConvertingIterable<Concept, String>(concepts) {
            @Override
            protected String convert(Concept o) {
                return o.getIRI();
            }
        });
        throw new VisalloException("Found multiple concepts for intent: " + intent + " (" + iris + ")");
    }

    @Deprecated
    @Override
    public String getConceptIRIByIntent(String intent) {
        return getConceptIRIByIntent(intent, PUBLIC);
    }

    @Override
    public String getConceptIRIByIntent(String intent, String workspaceId) {
        Concept concept = getConceptByIntent(intent, workspaceId);
        if (concept != null) {
            return concept.getIRI();
        }
        return null;
    }

    @Deprecated
    @Override
    public final Concept getRequiredConceptByIntent(String intent) {
        return getRequiredConceptByIntent(intent, PUBLIC);
    }

    @Override
    public Concept getRequiredConceptByIntent(String intent, String workspaceId) {
        Concept concept = getConceptByIntent(intent, workspaceId);
        if (concept == null) {
            throw new VisalloException("Could not find concept by intent: " + intent);
        }
        return concept;
    }

    @Deprecated
    @Override
    public final String getRequiredConceptIRIByIntent(String intent) {
        return getRequiredConceptIRIByIntent(intent, PUBLIC);
    }

    @Override
    public String getRequiredConceptIRIByIntent(String intent, String workspaceId) {
        return getRequiredConceptByIntent(intent, workspaceId).getIRI();
    }

    @Deprecated
    @Override
    public final Concept getRequiredConceptByIRI(String iri) {
        return getRequiredConceptByIRI(iri, PUBLIC);
    }

    @Override
    public Concept getRequiredConceptByIRI(String iri, String workspaceId) {
        Concept concept = getConceptByIRI(iri, workspaceId);
        if (concept == null) {
            throw new VisalloException("Could not find concept by IRI: " + iri);
        }
        return concept;
    }

    @Override
    public String generateDynamicIri(Class type, String displayName, String workspaceId, String... extended) {
        displayName = displayName.trim().replaceAll("\\s+", "_").toLowerCase();
        displayName = displayName.substring(0, Math.min(displayName.length(), MAX_DISPLAY_NAME));
        String typeIri = type.toString() + workspaceId + displayName;
        if (extended != null && extended.length > 0) {
            typeIri += Joiner.on("").join(extended);
        }

        return OntologyRepositoryBase.BASE_OWL_IRI +
                "/" +
                displayName.replaceAll("[^a-zA-Z0-9_]", "") +
                "#" +
                Hashing.sha1().hashString(typeIri, Charsets.UTF_8).toString();
    }

    @Override
    public Concept getEntityConcept() {
        return getEntityConcept(PUBLIC);
    }

    @Deprecated
    @Override
    public final Concept getOrCreateConcept(Concept parent, String conceptIRI, String displayName, File inDir) {
        return getOrCreateConcept(parent, conceptIRI, displayName, inDir, null, PUBLIC);
    }

    @Override
    public final Concept getOrCreateConcept(Concept parent, String conceptIRI, String displayName, File inDir, User user, String workspaceId) {
        return getOrCreateConcept(parent, conceptIRI, displayName, null, null, inDir, user, workspaceId);
    }

    @Override
    public final Concept getOrCreateConcept(Concept parent, String conceptIRI, String displayName, String glyphIconHref, String color, File inDir, User user, String workspaceId) {
        return getOrCreateConcept(parent, conceptIRI, displayName, glyphIconHref, color, inDir, true, user, workspaceId);
    }

    @Deprecated
    @Override
    public final Concept getOrCreateConcept(Concept parent, String conceptIRI, String displayName, File inDir, boolean deleteChangeableProperties) {
        return getOrCreateConcept(parent, conceptIRI, displayName, inDir, deleteChangeableProperties, null, PUBLIC);
    }

    @Override
    public final Concept getOrCreateConcept(Concept parent, String conceptIRI, String displayName, File inDir, boolean deleteChangeableProperties, User user, String workspaceId) {
        checkPrivileges(user, workspaceId);
        return internalGetOrCreateConcept(parent, conceptIRI, displayName, null, null, inDir, deleteChangeableProperties, user, workspaceId);
    }

    @Override
    public final Concept getOrCreateConcept(Concept parent, String conceptIRI, String displayName, String glyphIconHref, String color, File inDir, boolean deleteChangeableProperties, User user, String workspaceId) {
        checkPrivileges(user, workspaceId);
        return internalGetOrCreateConcept(parent, conceptIRI, displayName, glyphIconHref, color, inDir, deleteChangeableProperties, user, workspaceId);
    }

    protected abstract Concept internalGetOrCreateConcept(Concept parent, String conceptIRI, String displayName, String glyphIconHref, String color, File inDir, boolean deleteChangeableProperties, User user, String workspaceId);

    @Deprecated
    @Override
    public final Relationship getOrCreateRelationshipType(
            Relationship parent,
            Iterable<Concept> domainConcepts,
            Iterable<Concept> rangeConcepts,
            String relationshipIRI
    ) {
        return getOrCreateRelationshipType(parent, domainConcepts, rangeConcepts, relationshipIRI, null, true, null, PUBLIC);
    }

    @Deprecated
    @Override
    public final Relationship getOrCreateRelationshipType(
            Relationship parent,
            Iterable<Concept> domainConcepts,
            Iterable<Concept> rangeConcepts,
            String relationshipIRI,
            boolean deleteChangeableProperties
    ) {
        return getOrCreateRelationshipType(parent, domainConcepts, rangeConcepts, relationshipIRI, null, deleteChangeableProperties, null, PUBLIC);
    }

    @Override
    public final Relationship getOrCreateRelationshipType(
            Relationship parent,
            Iterable<Concept> domainConcepts,
            Iterable<Concept> rangeConcepts,
            String relationshipIRI,
            boolean isDeclaredInOntology,
            User user,
            String workspaceId
    ) {
        return getOrCreateRelationshipType(parent, domainConcepts, rangeConcepts, relationshipIRI, null, isDeclaredInOntology, user, workspaceId);
    }

    @Override
    public final Relationship getOrCreateRelationshipType(
            Relationship parent,
            Iterable<Concept> domainConcepts,
            Iterable<Concept> rangeConcepts,
            String relationshipIRI,
            String displayName,
            boolean isDeclaredInOntology,
            User user,
            String workspaceId
    ) {
        checkPrivileges(user, workspaceId);
        if (parent == null && !relationshipIRI.equals(TOP_OBJECT_PROPERTY_IRI)) {
            parent = getTopObjectPropertyRelationship(workspaceId);
        }
        return internalGetOrCreateRelationshipType(parent, domainConcepts, rangeConcepts, relationshipIRI, displayName, isDeclaredInOntology, user, workspaceId);
    }

    protected abstract Relationship internalGetOrCreateRelationshipType(
            Relationship parent,
            Iterable<Concept> domainConcepts,
            Iterable<Concept> rangeConcepts,
            String relationshipIRI,
            String displayName,
            boolean isDeclaredInOntology,
            User user,
            String workspaceId
    );

    @Deprecated
    @Override
    public final Relationship getRelationshipByIntent(String intent) {
        return getRelationshipByIntent(intent, PUBLIC);
    }

    @Override
    public Relationship getRelationshipByIntent(String intent, String workspaceId) {
        String configurationKey = CONFIG_INTENT_RELATIONSHIP_PREFIX + intent;
        String relationshipIri = getConfiguration().get(configurationKey, null);
        if (relationshipIri != null) {
            Relationship relationship = getRelationshipByIRI(relationshipIri, workspaceId);
            if (relationship == null) {
                throw new VisalloException("Could not find relationship by configuration key: " + configurationKey);
            }
            return relationship;
        }

        List<Relationship> relationships = findLoadedRelationshipsByIntent(intent, workspaceId);
        if (relationships.size() == 0) {
            return null;
        }
        if (relationships.size() == 1) {
            return relationships.get(0);
        }

        String iris = Joiner.on(',').join(new ConvertingIterable<Relationship, String>(relationships) {
            @Override
            protected String convert(Relationship o) {
                return o.getIRI();
            }
        });
        throw new VisalloException("Found multiple relationships for intent: " + intent + " (" + iris + ")");
    }

    @Deprecated
    @Override
    public final String getRelationshipIRIByIntent(String intent) {
        return getRelationshipIRIByIntent(intent, PUBLIC);
    }

    @Override
    public String getRelationshipIRIByIntent(String intent, String workspaceId) {
        Relationship relationship = getRelationshipByIntent(intent, workspaceId);
        if (relationship != null) {
            return relationship.getIRI();
        }
        return null;
    }

    @Deprecated
    @Override
    public final Relationship getRequiredRelationshipByIntent(String intent) {
        return getRequiredRelationshipByIntent(intent, PUBLIC);
    }

    @Override
    public Relationship getRequiredRelationshipByIntent(String intent, String workspaceId) {
        Relationship relationship = getRelationshipByIntent(intent, workspaceId);
        if (relationship == null) {
            throw new VisalloException("Could not find relationship by intent: " + intent);
        }
        return relationship;
    }

    @Deprecated
    @Override
    public final String getRequiredRelationshipIRIByIntent(String intent) {
        return getRequiredRelationshipIRIByIntent(intent, PUBLIC);
    }

    @Override
    public String getRequiredRelationshipIRIByIntent(String intent, String workspaceId) {
        return getRequiredRelationshipByIntent(intent, workspaceId).getIRI();
    }

    @Deprecated
    @Override
    public final OntologyProperty getPropertyByIntent(String intent) {
        return getPropertyByIntent(intent, PUBLIC);
    }

    @Override
    public OntologyProperty getPropertyByIntent(String intent, String workspaceId) {
        String configurationKey = CONFIG_INTENT_PROPERTY_PREFIX + intent;
        String propertyIri = getConfiguration().get(configurationKey, null);
        if (propertyIri != null) {
            OntologyProperty property = getPropertyByIRI(propertyIri, workspaceId);
            if (property == null) {
                throw new VisalloException("Could not find property by configuration key: " + configurationKey);
            }
            return property;
        }

        List<OntologyProperty> properties = getPropertiesByIntent(intent, workspaceId);
        if (properties.size() == 0) {
            return null;
        }
        if (properties.size() == 1) {
            return properties.get(0);
        }

        String iris = Joiner.on(',').join(new ConvertingIterable<OntologyProperty, String>(properties) {
            @Override
            protected String convert(OntologyProperty o) {
                return o.getTitle();
            }
        });
        throw new VisalloException("Found multiple properties for intent: " + intent + " (" + iris + ")");
    }

    @Deprecated
    @Override
    public final String getPropertyIRIByIntent(String intent) {
        return getPropertyIRIByIntent(intent, PUBLIC);
    }

    @Override
    public String getPropertyIRIByIntent(String intent, String workspaceId) {
        OntologyProperty prop = getPropertyByIntent(intent, workspaceId);
        if (prop != null) {
            return prop.getTitle();
        }
        return null;
    }

    @Deprecated
    @Override
    public final OntologyProperty getRequiredPropertyByIntent(String intent) {
        return getRequiredPropertyByIntent(intent, PUBLIC);
    }

    @Override
    public OntologyProperty getRequiredPropertyByIntent(String intent, String workspaceId) {
        OntologyProperty property = getPropertyByIntent(intent, workspaceId);
        if (property == null) {
            throw new VisalloException("Could not find property by intent: " + intent);
        }
        return property;
    }

    @Deprecated
    @Override
    public final String getRequiredPropertyIRIByIntent(String intent) {
        return getRequiredPropertyIRIByIntent(intent, PUBLIC);
    }

    @Override
    public String getRequiredPropertyIRIByIntent(String intent, String workspaceId) {
        return getRequiredPropertyByIntent(intent, workspaceId).getTitle();
    }

    @Deprecated
    @Override
    public final OntologyProperty getDependentPropertyParent(String iri) {
        return getDependentPropertyParent(iri, PUBLIC);
    }

    @Override
    public OntologyProperty getDependentPropertyParent(String iri, String workspaceId) {
        for (OntologyProperty property : getProperties(workspaceId)) {
            if (property.getDependentPropertyIris().contains(iri)) {
                return property;
            }
        }
        return null;
    }

    @Deprecated
    @Override
    public final <T extends VisalloProperty> T getVisalloPropertyByIntent(String intent, Class<T> visalloPropertyType) {
        return getVisalloPropertyByIntent(intent, visalloPropertyType, PUBLIC);
    }

    @Override
    public <T extends VisalloProperty> T getVisalloPropertyByIntent(String intent, Class<T> visalloPropertyType, String workspaceId) {
        String propertyIri = getPropertyIRIByIntent(intent, workspaceId);
        if (propertyIri == null) {
            LOGGER.warn("No property found for intent: %s", intent);
            return null;
        }
        try {
            Constructor<T> constructor = visalloPropertyType.getConstructor(String.class);
            return constructor.newInstance(propertyIri);
        } catch (Exception ex) {
            throw new VisalloException("Could not create property for intent: " + intent + " (propertyIri: " + propertyIri + ")");
        }
    }

    @Deprecated
    @Override
    public final <T extends VisalloProperty> T getRequiredVisalloPropertyByIntent(String intent, Class<T> visalloPropertyType) {
        return getRequiredVisalloPropertyByIntent(intent, visalloPropertyType, PUBLIC);
    }

    @Override
    public <T extends VisalloProperty> T getRequiredVisalloPropertyByIntent(String intent, Class<T> visalloPropertyType, String workspaceId) {
        T result = getVisalloPropertyByIntent(intent, visalloPropertyType, workspaceId);
        if (result == null) {
            throw new VisalloException("Could not find property by intent: " + intent);
        }
        return result;
    }

    @Deprecated
    @Override
    public final List<OntologyProperty> getPropertiesByIntent(String intent) {
        return getPropertiesByIntent(intent, PUBLIC);
    }

    @Override
    public List<OntologyProperty> getPropertiesByIntent(String intent, String workspaceId) {
        List<OntologyProperty> results = new ArrayList<>();
        for (OntologyProperty property : getProperties(workspaceId)) {
            String[] propertyIntents = property.getIntents();
            if (Arrays.asList(propertyIntents).contains(intent)) {
                results.add(property);
            }
        }
        return results;
    }

    @Deprecated
    @Override
    public final ClientApiOntology getClientApiObject() {
        return getClientApiObject(PUBLIC);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ClientApiOntology getClientApiObject(String workspaceId) {
        Ontology ontology = getOntology(workspaceId);
        Object[] results = ExecutorServiceUtil.runAllAndWait(
                () -> Concept.toClientApiConcepts(ontology.getConcepts()),
                () -> OntologyProperty.toClientApiProperties(ontology.getProperties()),
                () -> Relationship.toClientApiRelationships(ontology.getRelationships())
        );

        ClientApiOntology clientOntology = new ClientApiOntology();
        clientOntology.addAllConcepts((Collection<ClientApiOntology.Concept>) results[0]);
        clientOntology.addAllProperties((Collection<ClientApiOntology.Property>) results[1]);
        clientOntology.addAllRelationships((Collection<ClientApiOntology.Relationship>) results[2]);
        return clientOntology;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Ontology getOntology(String workspaceId) {
        if (workspaceId == null) {
            return getOntology(PUBLIC);
        }

        Ontology ontology = cacheService.getIfPresent(ONTOLOGY_CACHE_NAME, workspaceId);
        if (ontology != null) {
            return ontology;
        }
        Object[] results = ExecutorServiceUtil.runAllAndWait(
                () -> getConceptsWithProperties(workspaceId),
                () -> getRelationships(workspaceId),
                () -> getProperties(workspaceId)
        );
        Iterable<Concept> concepts = (Iterable<Concept>) results[0];
        Iterable<Relationship> relationships = (Iterable<Relationship>) results[1];
        Map<String, OntologyProperty> properties = stream((Iterable<OntologyProperty>) results[2])
                .collect(Collectors.toMap(OntologyProperty::getIri, p -> p));
        List<ExtendedDataTableProperty> extendedDataTables = properties.values().stream()
                .filter(p -> p instanceof ExtendedDataTableProperty)
                .map(p -> (ExtendedDataTableProperty) p)
                .collect(Collectors.toList());
        ontology = new Ontology(
                concepts,
                relationships,
                extendedDataTables,
                properties,
                workspaceId
        );

        // to avoid caching multiple unchanged ontologies
        if (!PUBLIC.equals(workspaceId) && ontology.getSandboxStatus() == SandboxStatus.PUBLIC) {
            ontology = getOntology(PUBLIC);
        }

        cacheService.put(ONTOLOGY_CACHE_NAME, workspaceId, ontology, ontologyCacheOptions);
        return ontology;
    }

    protected Relationship getTopObjectPropertyRelationship(String workspaceId) {
        return getRelationshipByIRI(TOP_OBJECT_PROPERTY_IRI, workspaceId);
    }

    @Override
    public void clearCache() {
        cacheService.invalidate(ONTOLOGY_CACHE_NAME);
    }

    @Override
    public void clearCache(String workspaceId) {
        cacheService.invalidate(ONTOLOGY_CACHE_NAME, workspaceId);
    }

    public final Configuration getConfiguration() {
        return configuration;
    }

    protected void defineRequiredProperties(Graph graph) {
        definePropertyOnGraph(graph, VisalloProperties.CONCEPT_TYPE, String.class, EnumSet.of(TextIndexHint.EXACT_MATCH));
        definePropertyOnGraph(graph, VisalloProperties.MODIFIED_BY, String.class, EnumSet.of(TextIndexHint.EXACT_MATCH));
        definePropertyOnGraph(graph, VisalloProperties.MODIFIED_DATE, Date.class, TextIndexHint.NONE);
        definePropertyOnGraph(graph, VisalloProperties.VISIBILITY_JSON, String.class, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.ONTOLOGY_TITLE, String.class, EnumSet.of(TextIndexHint.EXACT_MATCH));
        definePropertyOnGraph(graph, OntologyProperties.DISPLAY_NAME, String.class, EnumSet.of(TextIndexHint.EXACT_MATCH));
        definePropertyOnGraph(graph, OntologyProperties.INTENT, String.class, EnumSet.of(TextIndexHint.EXACT_MATCH));
        definePropertyOnGraph(graph, OntologyProperties.TITLE_FORMULA, String.class, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.SUBTITLE_FORMULA, String.class, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.TIME_FORMULA, String.class, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.GLYPH_ICON, byte[].class, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.MAP_GLYPH_ICON, byte[].class, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.GLYPH_ICON_FILE_NAME, String.class, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.DATA_TYPE, String.class, EnumSet.of(TextIndexHint.EXACT_MATCH));
        definePropertyOnGraph(graph, OntologyProperties.USER_VISIBLE, Boolean.TYPE, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.SEARCHABLE, Boolean.TYPE, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.SORTABLE, Boolean.TYPE, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.SORT_PRIORITY, Integer.TYPE, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.ADDABLE, Boolean.TYPE, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.DELETEABLE, Boolean.TYPE, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.UPDATEABLE, Boolean.TYPE, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.ONTOLOGY_FILE, byte[].class, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.ONTOLOGY_FILE_MD5, String.class, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.COLOR, String.class, TextIndexHint.NONE);
        definePropertyOnGraph(graph, OntologyProperties.DEPENDENT_PROPERTY_ORDER_PROPERTY_NAME, Integer.class, TextIndexHint.NONE);
    }

    protected void definePropertyOnGraph(Graph graph, VisalloPropertyBase<?, ?> property, Class dataType, Set<TextIndexHint> textIndexHint) {
        definePropertyOnGraph(graph, property.getPropertyName(), dataType, textIndexHint, null, false);
    }

    protected void definePropertyOnGraph(
            Graph graph,
            String propertyName,
            Class dataType,
            Collection<TextIndexHint> textIndexHint,
            Double boost,
            boolean sortable
    ) {
        if (!graph.isPropertyDefined(propertyName)) {
            DefinePropertyBuilder builder = graph.defineProperty(propertyName).dataType(dataType).sortable(sortable);
            if (textIndexHint != null) {
                builder.textIndexHint(textIndexHint);
            }
            if (boost != null) {
                if (graph.isFieldBoostSupported()) {
                    builder.boost(boost);
                } else {
                    LOGGER.warn("Field boosting is not support by the graph");
                }
            }
            builder.define();
        } else {
            PropertyDefinition propertyDefinition = graph.getPropertyDefinition(propertyName);
            if (propertyDefinition.getDataType() != dataType) {
                LOGGER.warn("Ontology property type mismatch for property %s! Expected %s but found %s",
                        propertyName, dataType.getName(), propertyDefinition.getDataType().getName());
            }

            Set definedTextHints = propertyDefinition.getTextIndexHints() == null ? Collections.EMPTY_SET : propertyDefinition.getTextIndexHints();
            if (!definedTextHints.equals(textIndexHint)) {
                LOGGER.warn("Ontology property text index hints mismatch for property %s! Expected %s but found %s",
                        propertyName, textIndexHint, definedTextHints);
            }
        }
    }

    protected boolean determineSearchable(
            String propertyIri,
            PropertyType dataType,
            Collection<TextIndexHint> textIndexHints,
            boolean searchable
    ) {
        if (dataType == PropertyType.EXTENDED_DATA_TABLE) {
            return false;
        }
        if (dataType == PropertyType.STRING) {
            checkNotNull(textIndexHints, "textIndexHints are required for string properties");
            if (searchable && (textIndexHints.isEmpty() || textIndexHints.equals(TextIndexHint.NONE))) {
                searchable = false;
            } else if (!searchable && (!textIndexHints.isEmpty() || !textIndexHints.equals(TextIndexHint.NONE))) {
                LOGGER.info("textIndexHints was specified for non-UI-searchable string property:: " + propertyIri);
            }
        }
        return searchable;
    }

    protected abstract Graph getGraph();

    protected abstract void internalDeleteConcept(Concept concept, String workspaceId);

    protected abstract void internalDeleteProperty(OntologyProperty property, String workspaceId);

    protected abstract void internalDeleteRelationship(Relationship relationship, String workspaceId);

    public void deleteConcept(String conceptTypeIri, User user, String workspaceId) {
        checkDeletePrivileges(user, workspaceId);

        Set<Concept> concepts = getConceptAndAllChildrenByIri(conceptTypeIri, workspaceId);
        if (concepts.size() == 1) {
            for (Concept concept : concepts) {
                if (concept.getSandboxStatus().equals(SandboxStatus.PRIVATE)) {
                    for (Relationship relationship : getRelationships(workspaceId)) {
                        if (relationship.getDomainConceptIRIs().contains(conceptTypeIri) ||
                                relationship.getRangeConceptIRIs().contains(conceptTypeIri)) {
                            throw new VisalloException("Unable to delete concept that is used in domain/range of relationship");
                        }
                    }
                    Graph graph = getGraph();
                    Authorizations authorizations = graph.createAuthorizations(workspaceId);
                    GraphQuery query = graph.query(authorizations);
                    addConceptTypeFilterToQuery(query, concept.getIRI(), false, workspaceId);
                    query.limit(0);
                    long results = query.search().getTotalHits();
                    if (results == 0) {
                        List<OntologyProperty> removeProperties = concept.getProperties().stream().filter(ontologyProperty ->
                                ontologyProperty.getSandboxStatus().equals(SandboxStatus.PRIVATE) &&
                                        ontologyProperty.getRelationshipIris().size() == 0 &&
                                        ontologyProperty.getConceptIris().size() == 1 &&
                                        ontologyProperty.getConceptIris().get(0).equals(conceptTypeIri)
                        ).collect(Collectors.toList());

                        internalDeleteConcept(concept, workspaceId);

                        for (OntologyProperty property : removeProperties) {
                            internalDeleteProperty(property, workspaceId);
                        }
                    } else {
                        throw new VisalloException("Unable to delete concept that have vertices assigned to it");
                    }
                } else {
                    throw new VisalloException("Unable to delete published concepts");
                }
            }
        } else {
            throw new VisalloException("Unable to delete concept that have children");
        }
    }


    public void deleteProperty(String propertyIri, User user, String workspaceId) {
        checkDeletePrivileges(user, workspaceId);

        OntologyProperty property = getPropertyByIRI(propertyIri, workspaceId);
        if (property != null) {
            if (property.getSandboxStatus().equals(SandboxStatus.PRIVATE)) {
                Graph graph = getGraph();
                Authorizations authorizations = graph.createAuthorizations(workspaceId);
                GraphQuery query = graph.query(authorizations);
                query.has(propertyIri);
                query.limit(0);
                long results = query.search().getTotalHits();
                if (results == 0) {
                    internalDeleteProperty(property, workspaceId);
                } else {
                    throw new VisalloException("Unable to delete property that have elements using it");
                }
            } else {
                throw new VisalloException("Unable to delete published properties");
            }

        } else throw new VisalloResourceNotFoundException("Property not found");
    }


    public void deleteRelationship(String relationshipIri, User user, String workspaceId) {
        checkDeletePrivileges(user, workspaceId);

        Set<Relationship> relationships = getRelationshipAndAllChildrenByIRI(relationshipIri, workspaceId);
        if (relationships.size() == 1) {
            for (Relationship relationship : relationships) {
                if (relationship.getSandboxStatus().equals(SandboxStatus.PRIVATE)) {
                    Graph graph = getGraph();
                    Authorizations authorizations = graph.createAuthorizations(workspaceId);
                    GraphQuery query = graph.query(authorizations);
                    addEdgeLabelFilterToQuery(query, relationshipIri, false, workspaceId);
                    query.limit(0);
                    long results = query.search().getTotalHits();
                    if (results == 0) {
                        List<OntologyProperty> removeProperties = relationship.getProperties().stream().filter(ontologyProperty ->
                                ontologyProperty.getSandboxStatus().equals(SandboxStatus.PRIVATE) &&
                                        ontologyProperty.getConceptIris().size() == 0 &&
                                        ontologyProperty.getRelationshipIris().size() == 1 &&
                                        ontologyProperty.getRelationshipIris().get(0).equals(relationshipIri)
                        ).collect(Collectors.toList());
                        internalDeleteRelationship(relationship, workspaceId);

                        for (OntologyProperty property : removeProperties) {
                            internalDeleteProperty(property, workspaceId);
                        }
                    } else {
                        throw new VisalloException("Unable to delete relationship that have edges using it");
                    }
                } else {
                    throw new VisalloException("Unable to delete published relationships");
                }
            }
        } else throw new VisalloException("Unable to delete relationship that have children");
    }

    @Deprecated
    @Override
    public final void addConceptTypeFilterToQuery(Query query, String conceptTypeIri, boolean includeChildNodes) {
        addConceptTypeFilterToQuery(query, conceptTypeIri, includeChildNodes, PUBLIC);
    }

    @Override
    public void addConceptTypeFilterToQuery(Query query, String conceptTypeIri, boolean includeChildNodes, String workspaceId) {
        checkNotNull(conceptTypeIri, "conceptTypeIri cannot be null");
        List<ElementTypeFilter> filters = new ArrayList<>();
        filters.add(new ElementTypeFilter(conceptTypeIri, includeChildNodes));
        addConceptTypeFilterToQuery(query, filters, workspaceId);
    }

    @Deprecated
    @Override
    public final void addConceptTypeFilterToQuery(Query query, Collection<ElementTypeFilter> filters) {
        addConceptTypeFilterToQuery(query, filters, PUBLIC);
    }

    @Override
    public void addConceptTypeFilterToQuery(Query query, Collection<ElementTypeFilter> filters, String workspaceId) {
        checkNotNull(query, "query cannot be null");
        checkNotNull(filters, "filters cannot be null");

        if (filters.isEmpty()) {
            return;
        }

        Set<String> conceptIds = new HashSet<>(filters.size());

        for (ElementTypeFilter filter : filters) {
            Concept concept = getConceptByIRI(filter.iri, workspaceId);
            checkNotNull(concept, "Could not find concept with IRI: " + filter.iri);

            conceptIds.add(concept.getIRI());

            if (filter.includeChildNodes) {
                Set<Concept> childConcepts = getConceptAndAllChildren(concept, workspaceId);
                conceptIds.addAll(childConcepts.stream().map(Concept::getIRI).collect(Collectors.toSet()));
            }
        }

        query.has(VisalloProperties.CONCEPT_TYPE.getPropertyName(), Contains.IN, conceptIds);
    }

    @Deprecated
    @Override
    public final void addEdgeLabelFilterToQuery(Query query, String edgeLabel, boolean includeChildNodes) {
        addEdgeLabelFilterToQuery(query, edgeLabel, includeChildNodes, PUBLIC);
    }

    @Override
    public void addEdgeLabelFilterToQuery(Query query, String edgeLabel, boolean includeChildNodes, String workspaceId) {
        checkNotNull(edgeLabel, "edgeLabel cannot be null");
        List<ElementTypeFilter> filters = new ArrayList<>();
        filters.add(new ElementTypeFilter(edgeLabel, includeChildNodes));
        addEdgeLabelFilterToQuery(query, filters, workspaceId);
    }

    @Deprecated
    @Override
    public final void addEdgeLabelFilterToQuery(Query query, Collection<ElementTypeFilter> filters) {
        addEdgeLabelFilterToQuery(query, filters, PUBLIC);
    }

    @Override
    public void addEdgeLabelFilterToQuery(Query query, Collection<ElementTypeFilter> filters, String workspaceId) {
        checkNotNull(filters, "filters cannot be null");

        if (filters.isEmpty()) {
            return;
        }

        Set<String> edgeIds = new HashSet<>(filters.size());

        for (ElementTypeFilter filter : filters) {
            Relationship relationship = getRelationshipByIRI(filter.iri, workspaceId);
            checkNotNull(relationship, "Could not find edge with IRI: " + filter.iri);

            edgeIds.add(relationship.getIRI());

            if (filter.includeChildNodes) {
                Set<Relationship> childRelations = getRelationshipAndAllChildren(relationship, workspaceId);
                edgeIds.addAll(childRelations.stream().map(Relationship::getIRI).collect(Collectors.toSet()));
            }
        }

        query.hasEdgeLabel(edgeIds);
    }

    @Override
    public final void publishConcept(Concept concept, User user, String workspaceId) {
        checkPrivileges(user, null);
        internalPublishConcept(concept, user, workspaceId);
    }

    public abstract void internalPublishConcept(Concept concept, User user, String workspaceId);

    @Override
    public final void publishRelationship(Relationship relationship, User user, String workspaceId) {
        checkPrivileges(user, null);
        internalPublishRelationship(relationship, user, workspaceId);
    }

    public abstract void internalPublishRelationship(Relationship relationship, User user, String workspaceId);

    @Override
    public void publishProperty(OntologyProperty property, User user, String workspaceId) {
        checkPrivileges(user, null);
        internalPublishProperty(property, user, workspaceId);
    }

    public abstract void internalPublishProperty(OntologyProperty property, User user, String workspaceId);

    protected void checkPrivileges(User user, String workspaceId) {
        if (user != null && user.getUserType() == UserType.SYSTEM) {
            return;
        }

        if (user == null) {
            throw new VisalloAccessDeniedException("You must provide a valid user to perform this action", null, null);
        }

        if (isPublic(workspaceId)) {
            if (!getPrivilegeRepository().hasPrivilege(user, Privilege.ONTOLOGY_PUBLISH)) {
                throw new VisalloAccessDeniedException("User does not have ONTOLOGY_PUBLISH privilege", user, null);
            }
        } else {
            List<WorkspaceUser> users = getWorkspaceRepository().findUsersWithAccess(workspaceId, user);
            boolean access = users.stream()
                    .anyMatch(workspaceUser ->
                            workspaceUser.getUserId().equals(user.getUserId()) &&
                                    workspaceUser.getWorkspaceAccess().equals(WorkspaceAccess.WRITE));

            if (!access) {
                throw new VisalloAccessDeniedException("User does not have access to workspace", user, null);
            }

            if (!getPrivilegeRepository().hasPrivilege(user, Privilege.ONTOLOGY_ADD)) {
                throw new VisalloAccessDeniedException("User does not have ONTOLOGY_ADD privilege", user, null);
            }
        }
    }

    protected void checkDeletePrivileges(User user, String workspaceId) {
        if (user != null && user.getUserType() == UserType.SYSTEM) {
            return;
        }

        if (user == null) {
            throw new VisalloAccessDeniedException("You must provide a valid user to perform this action", null, null);
        }

        if (workspaceId == null) {
            throw new VisalloAccessDeniedException("User does not have access to delete published ontology items", user, null);
        } else if (!getPrivilegeRepository().hasPrivilege(user, Privilege.ADMIN)) {
            throw new VisalloAccessDeniedException("User does not have admin privilege", user, null);
        }
    }


    protected List<Concept> findLoadedConceptsByIntent(String intent, String workspaceId) {
        List<Concept> results = new ArrayList<>();
        for (Concept concept : getConceptsWithProperties(workspaceId)) {
            String[] conceptIntents = concept.getIntents();
            if (Arrays.asList(conceptIntents).contains(intent)) {
                results.add(concept);
            }
        }
        return results;
    }

    protected List<Relationship> findLoadedRelationshipsByIntent(String intent, String workspaceId) {
        List<Relationship> results = new ArrayList<>();
        for (Relationship relationship : getRelationships(workspaceId)) {
            String[] relationshipIntents = relationship.getIntents();
            if (Arrays.asList(relationshipIntents).contains(intent)) {
                results.add(relationship);
            }
        }
        return results;
    }

    protected User getSystemUser() {
        return new SystemUser();
    }

    protected PrivilegeRepository getPrivilegeRepository() {
        if (privilegeRepository == null) {
            privilegeRepository = InjectHelper.getInstance(PrivilegeRepository.class);
        }
        return privilegeRepository;
    }

    protected WorkspaceRepository getWorkspaceRepository() {
        if (workspaceRepository == null) {
            workspaceRepository = InjectHelper.getInstance(WorkspaceRepository.class);
        }
        return workspaceRepository;
    }

    protected abstract void deleteChangeableProperties(OntologyElement element, Authorizations authorizations);

    protected abstract void deleteChangeableProperties(OntologyProperty property, Authorizations authorizations);

    protected boolean isPublic(String workspaceId) {
        return workspaceId == null || PUBLIC.equals(workspaceId);
    }

    protected void validateRelationship(String relationshipIRI,
                                        Iterable<Concept> domainConcepts,
                                        Iterable<Concept> rangeConcepts) {
        if (!relationshipIRI.equals(TOP_OBJECT_PROPERTY_IRI)
                && (IterableUtils.isEmpty(domainConcepts) || IterableUtils.isEmpty(rangeConcepts))) {
            throw new VisalloException(relationshipIRI + " must have at least one domain and range ");
        }
    }
}
