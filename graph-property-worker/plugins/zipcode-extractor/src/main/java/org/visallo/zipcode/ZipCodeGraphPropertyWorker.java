package org.visallo.zipcode;

import org.visallo.core.ingest.graphProperty.GraphPropertyWorkerPrepareData;
import org.visallo.core.ingest.graphProperty.RegexGraphPropertyWorker;
import org.visallo.core.model.Description;
import org.visallo.core.model.Name;
import org.visallo.core.model.ontology.Concept;

import static org.visallo.core.model.ontology.OntologyRepository.PUBLIC;

@Name("ZipCode Extractor")
@Description("Extracts ZipCode from text")
public class ZipCodeGraphPropertyWorker extends RegexGraphPropertyWorker {
    private static final String ZIPCODE_REG_EX = "\\b\\d{5}-\\d{4}\\b|\\b\\d{5}\\b";
    public static final String ZIPCODE_CONCEPT_INTENT = "zipCode";
    private Concept concept;

    public ZipCodeGraphPropertyWorker() {
        super(ZIPCODE_REG_EX);
    }

    @Override
    protected Concept getConcept() {
        return concept;
    }

    @Override
    public void prepare(GraphPropertyWorkerPrepareData workerPrepareData) throws Exception {
        this.concept = getOntologyRepository().getRequiredConceptByIntent(ZIPCODE_CONCEPT_INTENT, PUBLIC);
        super.prepare(workerPrepareData);
    }
}
