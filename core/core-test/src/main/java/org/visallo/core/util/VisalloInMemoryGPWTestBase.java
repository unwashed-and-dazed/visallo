package org.visallo.core.util;

import com.google.inject.Injector;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.vertexium.Authorizations;
import org.vertexium.Element;
import org.vertexium.Property;
import org.vertexium.TextIndexHint;
import org.vertexium.property.StreamingPropertyValue;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.ingest.graphProperty.*;
import org.visallo.core.model.ontology.Concept;
import org.visallo.core.model.ontology.OntologyPropertyDefinition;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.workQueue.Priority;
import org.visallo.core.security.VisalloVisibility;
import org.visallo.core.user.User;
import org.visallo.vertexium.model.user.InMemoryUser;
import org.visallo.web.clientapi.model.PropertyType;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VisalloInMemoryGPWTestBase extends VisalloInMemoryTestBase {
    private User user;

    @Override
    public void before() throws Exception {
        super.before();
        user = null;
    }

    protected void run(GraphPropertyWorker gpw, GraphPropertyWorkerPrepareData workerPrepareData, Element element) {
        run(gpw, workerPrepareData, element, null);
    }

    protected void run(
            GraphPropertyWorker gpw,
            GraphPropertyWorkerPrepareData workerPrepareData,
            Element element,
            String workspaceId
    ) {
        String visibilitySource = getVisibilitySource(element);
        run(gpw, workerPrepareData, element, null, null, workspaceId, null, visibilitySource);
        for (Property property : element.getProperties()) {
            InputStream in = null;
            if (property.getValue() instanceof StreamingPropertyValue) {
                StreamingPropertyValue spv = (StreamingPropertyValue) property.getValue();
                in = spv.getInputStream();
            }
            run(gpw, workerPrepareData, element, property, in, workspaceId, null, visibilitySource);
        }
    }

    protected boolean run(
            GraphPropertyWorker gpw,
            GraphPropertyWorkerPrepareData workerPrepareData,
            Element element,
            Property property,
            InputStream in
    ) {
        return run(gpw, workerPrepareData, element, property, in, null, null, null);
    }

    protected boolean run(
            GraphPropertyWorker gpw,
            GraphPropertyWorkerPrepareData workerPrepareData,
            Element element,
            Property property,
            InputStream in,
            String workspaceId,
            ElementOrPropertyStatus status
    ) {
        return run(gpw, workerPrepareData, element, property, in, workspaceId, status, null);
    }

    protected boolean run(
            GraphPropertyWorker gpw,
            GraphPropertyWorkerPrepareData workerPrepareData,
            Element element,
            Property property,
            InputStream in,
            String workspaceId,
            ElementOrPropertyStatus status,
            String visibilitySource
    ) {
        try {
            gpw.setOntologyRepository(getOntologyRepository());
            gpw.setWorkspaceRepository(getWorkspaceRepository());
            gpw.setConfiguration(getConfiguration());
            gpw.setGraph(getGraph());
            gpw.setVisibilityTranslator(getVisibilityTranslator());
            gpw.setWorkQueueRepository(getWorkQueueRepository());
            gpw.setGraphRepository(getGraphRepository());
            gpw.prepare(workerPrepareData);
        } catch (Exception ex) {
            throw new VisalloException("Failed to prepare: " + gpw.getClass().getName(), ex);
        }

        try {
            if (!(status == ElementOrPropertyStatus.HIDDEN && gpw.isHiddenHandled(element, property))
                    && !(status == ElementOrPropertyStatus.DELETION && gpw.isDeleteHandled(element, property))
                    && !gpw.isHandled(element, property)) {
                return false;
            }
        } catch (Exception ex) {
            throw new VisalloException("Failed isHandled: " + gpw.getClass().getName(), ex);
        }

        try {
            GraphPropertyWorkData workData = new GraphPropertyWorkData(
                    getVisibilityTranslator(),
                    element,
                    property,
                    workspaceId,
                    visibilitySource,
                    Priority.NORMAL,
                    false,
                    (property == null ? element.getTimestamp() : property.getTimestamp()) - 1,
                    status
            );
            if (gpw.isLocalFileRequired() && workData.getLocalFile() == null && in != null) {
                byte[] data = IOUtils.toByteArray(in);
                File tempFile = File.createTempFile("visalloTest", "data");
                FileUtils.writeByteArrayToFile(tempFile, data);
                workData.setLocalFile(tempFile);
                in = new ByteArrayInputStream(data);
            }
            gpw.execute(in, workData);
        } catch (Exception ex) {
            throw new VisalloException("Failed to execute: " + gpw.getClass().getName(), ex);
        }
        return true;
    }

    private String getVisibilitySource(Element e) {
        String visibilitySource = null;
        if (e != null) {
            VisibilityJson visibilitySourceJson = VisalloProperties.VISIBILITY_JSON.getPropertyValue(e, null);
            if (visibilitySourceJson != null) {
                visibilitySource = visibilitySourceJson.getSource();
            }
        }
        return visibilitySource;
    }

    protected GraphPropertyWorkerPrepareData createWorkerPrepareData() {
        return createWorkerPrepareData(null, null, null, null);
    }

    protected GraphPropertyWorkerPrepareData createWorkerPrepareData(
            List<TermMentionFilter> termMentionFilters,
            User user,
            Authorizations authorizations,
            Injector injector
    ) {
        Map configuration = getConfigurationMap();
        if (termMentionFilters == null) {
            termMentionFilters = new ArrayList<>();
        }
        if (user == null) {
            user = getUser();
        }
        if (authorizations == null) {
            authorizations = getGraphAuthorizations(user, VisalloVisibility.SUPER_USER_VISIBILITY_STRING);
        }
        return new GraphPropertyWorkerPrepareData(configuration, termMentionFilters, user, authorizations, injector);
    }

    protected User getUser() {
        if (user == null) {
            user = new InMemoryUser("test", "Test User", "test@visallo.org", null);
        }
        return user;
    }

    protected void addPropertyWithIntent(String propertyIri, String... intents) {
        getOntologyRepository().getOrCreateProperty(
                new OntologyPropertyDefinition(
                        new ArrayList<>(),
                        propertyIri,
                        propertyIri,
                        PropertyType.STRING
                )
                        .setIntents(intents)
                        .setTextIndexHints(TextIndexHint.ALL),
                getUserRepository().getSystemUser(),
                null
        );
    }

    protected void addConceptWithIntent(String conceptIri, String... intents) {
        Concept concept = getOntologyRepository().getOrCreateConcept(
                null,
                conceptIri,
                conceptIri,
                null,
                getUserRepository().getSystemUser(),
                null
        );
        for (String intent : intents) {
            concept.addIntent(intent, getUserRepository().getSystemUser(), getGraphAuthorizations());
        }
    }
}
