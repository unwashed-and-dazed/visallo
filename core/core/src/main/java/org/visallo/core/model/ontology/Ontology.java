package org.visallo.core.model.ontology;

import org.visallo.web.clientapi.model.SandboxStatus;

import java.util.*;
import java.util.stream.Collectors;

import static org.visallo.core.util.StreamUtil.stream;

public class Ontology {
    private final String workspaceId;
    private final Map<String, Concept> conceptsByIri;
    private final Map<String, Relationship> relationshipsByIri;
    private final Map<String, ExtendedDataTableProperty> extendedDataTablesByIri;
    private final Map<String, OntologyProperty> propertiesByIri;

    public Ontology(
            Iterable<Concept> concepts,
            Iterable<Relationship> relationships,
            Iterable<ExtendedDataTableProperty> extendedDataTables,
            Map<String, OntologyProperty> propertiesByIri,
            String workspaceId
    ) {
        this.workspaceId = workspaceId;

        Map<String, OntologyProperty> propertyMap = new HashMap<>();

        conceptsByIri = Collections.unmodifiableMap(stream(concepts)
                .collect(Collectors.toMap(Concept::getIRI, concept -> {
                    Collection<OntologyProperty> properties = concept.getProperties();
                    if (properties != null && properties.size() > 0) {
                        properties.forEach(property -> propertyMap.put(property.getIri(), property));
                    }
                    return concept;
                })));
        relationshipsByIri = Collections.unmodifiableMap(stream(relationships)
                .collect(Collectors.toMap(Relationship::getIRI, relationship -> {
                    Collection<OntologyProperty> properties = relationship.getProperties();
                    if (properties != null && properties.size() > 0) {
                        properties.forEach(property -> propertyMap.put(property.getIri(), property));
                    }
                    return relationship;
                })));
        extendedDataTablesByIri = Collections.unmodifiableMap(stream(extendedDataTables)
                .collect(Collectors.toMap(ExtendedDataTableProperty::getIri, table -> {
                    List<OntologyProperty> properties = stream(table.getTablePropertyIris())
                            .map(propertiesByIri::get)
                            .collect(Collectors.toList());
                    properties.forEach(property -> propertyMap.put(property.getIri(), property));
                    return table;
                })));

        this.propertiesByIri = Collections.unmodifiableMap(propertyMap);
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public Collection<Concept> getConcepts() {
        return conceptsByIri.values();
    }

    public Map<String, Concept> getConceptsByIri() {
        return conceptsByIri;
    }

    public Concept getConceptByIri(String iri) {
        return conceptsByIri.get(iri);
    }

    public Collection<Relationship> getRelationships() {
        return relationshipsByIri.values();
    }

    public Map<String, Relationship> getRelationshipsByIri() {
        return relationshipsByIri;
    }

    public Relationship getRelationshipByIri(String iri) {
        return relationshipsByIri.get(iri);
    }

    public Collection<OntologyProperty> getProperties() {
        return propertiesByIri.values();
    }

    public Map<String, OntologyProperty> getPropertiesByIri() {
        return propertiesByIri;
    }

    public OntologyProperty getPropertyByIri(String iri) {
        return propertiesByIri.get(iri);
    }

    public Map<String, ExtendedDataTableProperty> getExtendedDataTablesByIri() {
        return extendedDataTablesByIri;
    }

    public SandboxStatus getSandboxStatus() {
        for (Concept concept : getConcepts()) {
            SandboxStatus sandboxStatus = concept.getSandboxStatus();
            if (sandboxStatus != SandboxStatus.PUBLIC) {
                return sandboxStatus;
            }
        }

        for (Relationship relationship : getRelationships()) {
            SandboxStatus sandboxStatus = relationship.getSandboxStatus();
            if (sandboxStatus != SandboxStatus.PUBLIC) {
                return sandboxStatus;
            }
        }

        for (OntologyProperty property : getProperties()) {
            SandboxStatus sandboxStatus = property.getSandboxStatus();
            if (sandboxStatus != SandboxStatus.PUBLIC) {
                return sandboxStatus;
            }
        }

        return SandboxStatus.PUBLIC;
    }
}
