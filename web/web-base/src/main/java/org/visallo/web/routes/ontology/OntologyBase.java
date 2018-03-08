package org.visallo.web.routes.ontology;

import org.visallo.webster.ParameterizedHandler;
import org.vertexium.util.IterableUtils;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.ontology.Concept;
import org.visallo.core.model.ontology.OntologyProperty;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.model.ontology.Relationship;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

abstract class OntologyBase implements ParameterizedHandler {
    private final OntologyRepository ontologyRepository;

    OntologyBase(OntologyRepository ontologyRepository) {
        this.ontologyRepository = ontologyRepository;
    }

    List<Concept> ontologyIrisToConcepts(String[] iris, String workspaceId) {
        return getOntologyObjects(iris, ontologyRepository::getConceptsByIRI, Concept::getIRI, "concept", workspaceId);
    }

    List<Relationship> ontologyIrisToRelationships(String[] iris, String workspaceId) {
        return getOntologyObjects(iris, ontologyRepository::getRelationshipsByIRI, Relationship::getIRI, "relationship", workspaceId);
    }

    List<OntologyProperty> ontologyIrisToProperties(String[] iris, String workspaceId) {
        return getOntologyObjects(iris, ontologyRepository::getPropertiesByIRI, OntologyProperty::getId, "property", workspaceId);
    }

    List<Concept> ontologyIdsToConcepts(String[] ids, String workspaceId) {
        return getOntologyObjects(ids, ontologyRepository::getConcepts, Concept::getIRI, "concept", workspaceId);
    }

    List<Relationship> ontologyIdsToRelationships(String[] ids, String workspaceId) {
        return getOntologyObjects(ids, ontologyRepository::getRelationships, Relationship::getIRI, "relationship", workspaceId);
    }

    List<OntologyProperty> ontologyIdsToProperties(String[] ids, String workspaceId) {
        return getOntologyObjects(ids, ontologyRepository::getProperties, OntologyProperty::getId, "property", workspaceId);
    }

    private <T> List<T> getOntologyObjects(
            String[] iris,
            BiFunction<List<String>, String, Iterable<T>> getAllByIriFunction,
            Function<T, String> getIriFunction,
            String ontologyObjectType,
            String workspaceId
    ) {
        if (iris == null) {
            return new ArrayList<>();
        }

        List<T> ontologyObjects = IterableUtils.toList(getAllByIriFunction.apply(Arrays.asList(iris), workspaceId));
        if (ontologyObjects.size() != iris.length) {
            List<String> foundIris = ontologyObjects.stream().map(getIriFunction).collect(Collectors.toList());
            String missingIris = Arrays.stream(iris).filter(iri -> !foundIris.contains(iri)).collect(Collectors.joining(", "));
            throw new VisalloException("Unable to load " + ontologyObjectType + " with IRI: " + missingIris);
        }
        return ontologyObjects;
    }
}
