package org.visallo.vertexium.model.notification;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.json.JSONObject;
import org.vertexium.*;
import org.vertexium.query.Compare;
import org.vertexium.query.Query;
import org.vertexium.query.SortDirection;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.graph.GraphRepository;
import org.visallo.core.model.graph.GraphUpdateContext;
import org.visallo.core.model.notification.NotificationOntology;
import org.visallo.core.model.notification.SystemNotification;
import org.visallo.core.model.notification.SystemNotificationRepository;
import org.visallo.core.model.notification.SystemNotificationSeverity;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.model.properties.types.PropertyMetadata;
import org.visallo.core.model.user.GraphAuthorizationRepository;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.model.workQueue.Priority;
import org.visallo.core.security.VisalloVisibility;
import org.visallo.core.user.User;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.visallo.core.util.StreamUtil.stream;

@Singleton
public class VertexiumSystemNotificationRepository extends SystemNotificationRepository {
    private static final String SYSTEM_NOTIFICATION_VERTEX_ID = "SYSTEM_NOTIFICATIONS";

    @Inject
    public VertexiumSystemNotificationRepository(
            Graph graph,
            GraphRepository graphRepository,
            GraphAuthorizationRepository graphAuthorizationRepository,
            UserRepository userRepository
    ) {
        super(graph, graphRepository, graphAuthorizationRepository, userRepository);
    }

    @Override
    public List<SystemNotification> getActiveNotifications(User user) {
        Authorizations authorizations = getAuthorizations(user);
        Date now = new Date();
        // TODO combine this when Vertexium has 'or'
        List<SystemNotification> withEndDates = stream(getGraph().query(authorizations)
                .hasExtendedData(NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE)
                .has(NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_START_DATE.getColumnName(), Compare.LESS_THAN_EQUAL, now)
                .has(NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_END_DATE.getColumnName(), Compare.GREATER_THAN_EQUAL, now)
                .extendedDataRows())
                .map(this::toSystemNotification)
                .collect(Collectors.toList());
        List<SystemNotification> withoutEndDates = stream(getGraph().query(authorizations)
                .hasExtendedData(NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE)
                .has(NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_START_DATE.getColumnName(), Compare.LESS_THAN_EQUAL, now)
                .hasNot(NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_END_DATE.getColumnName())
                .extendedDataRows())
                .map(this::toSystemNotification)
                .collect(Collectors.toList());
        ArrayList<SystemNotification> all = new ArrayList<>();
        all.addAll(withEndDates);
        all.addAll(withoutEndDates);
        all.sort(Comparator.comparing(SystemNotification::getStartDate));
        return all;
    }

    @Override
    public SystemNotification createNotification(
            SystemNotificationSeverity severity,
            String title,
            String message,
            String actionEvent,
            JSONObject actionPayload,
            Date startDate,
            Date endDate,
            User user
    ) {
        if (startDate == null) {
            startDate = new Date();
        }
        SystemNotification notification = new SystemNotification(startDate, title, message, actionEvent, actionPayload);
        notification.setSeverity(severity);
        notification.setStartDate(startDate);
        notification.setEndDate(endDate);
        return updateNotification(notification, user);
    }

    @Override
    public SystemNotification updateNotification(SystemNotification notification, User user) {
        Authorizations authorizations = getAuthorizations(user);
        try (GraphUpdateContext ctx = getGraphRepository().beginGraphUpdate(Priority.NORMAL, user, authorizations)) {
            ctx.update(getSystemNotificationVertex(ctx), elemCtx -> {
                String row = notification.getId();
                VisibilityJson visibilityJson = new VisibilityJson();
                Visibility visibility = getVisibility();
                PropertyMetadata metadata = new PropertyMetadata(user, visibilityJson, visibility);
                NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_TITLE.addExtendedData(elemCtx, row, notification.getTitle(), metadata);
                if (notification.getActionEvent() != null && notification.getActionPayload() != null) {
                    NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_ACTION_EVENT.addExtendedData(elemCtx, row, notification.getActionEvent(), metadata);
                    NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_ACTION_PAYLOAD.addExtendedData(elemCtx, row, notification.getActionPayload(), metadata);
                }
                NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_MESSAGE.addExtendedData(elemCtx, row, notification.getMessage(), metadata);
                NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_SEVERITY.addExtendedData(elemCtx, row, notification.getSeverity(), metadata);
                NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_START_DATE.addExtendedData(elemCtx, row, notification.getStartDate(), metadata);
                if (notification.getEndDate() != null) {
                    NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_END_DATE.addExtendedData(elemCtx, row, notification.getEndDate(), metadata);
                }
            });
        }
        return notification;
    }

    private Visibility getVisibility() {
        return new VisalloVisibility(VISIBILITY_STRING).getVisibility();
    }

    private Vertex getSystemNotificationVertex(GraphUpdateContext ctx) {
        try {
            return ctx.getOrCreateVertexAndUpdate(SYSTEM_NOTIFICATION_VERTEX_ID, getVisibility(), elemCtx -> {
                if (elemCtx.isNewElement()) {
                    elemCtx.setConceptType(OntologyRepository.ENTITY_CONCEPT_IRI);
                    elemCtx.updateBuiltInProperties(new Date(), new VisibilityJson());
                }
            }).get();
        } catch (Exception ex) {
            throw new VisalloException("Could not get system notification vertex");
        }
    }

    @Override
    public SystemNotification getNotification(String id, User user) {
        Authorizations authorizations = getAuthorizations(user);
        ArrayList<ExtendedDataRow> notifications = Lists.newArrayList(getGraph().query(authorizations)
                // TODO use hadId -- .has("id", id)
                .hasExtendedData(NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE)
                .extendedDataRows());
        for (ExtendedDataRow notification : notifications) {
            if (notification.getId().getRowId().equals(id)) {
                return toSystemNotification(notification);
            }
        }
        return null;
    }

    private SystemNotification toSystemNotification(ExtendedDataRow row) {
        SystemNotification notification = new SystemNotification(
                row.getId().getRowId(),
                NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_TITLE.getValue(row),
                NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_MESSAGE.getValue(row),
                NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_ACTION_EVENT.getValue(row),
                NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_ACTION_PAYLOAD.getValue(row)
        );
        notification.setStartDate(NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_START_DATE.getValue(row));
        notification.setEndDate(NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_END_DATE.getValue(row));
        notification.setSeverity(NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_SEVERITY.getValue(row));
        return notification;
    }

    @Override
    public List<SystemNotification> getFutureNotifications(Date maxDate, User user) {
        Authorizations authorizations = getAuthorizations(user);
        Date now = new Date();
        Query query = getGraph().query(authorizations)
                .hasExtendedData(NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE)
                .has(NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_START_DATE.getColumnName(), Compare.GREATER_THAN_EQUAL, now);
        if (maxDate != null) {
            query = query.has(NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_END_DATE.getColumnName(), Compare.LESS_THAN_EQUAL, maxDate);
        }
        return stream(query
                .sort(NotificationOntology.SYSTEM_NOTIFICATIONS_TABLE_START_DATE.getColumnName(), SortDirection.ASCENDING)
                .extendedDataRows())
                .map(this::toSystemNotification)
                .collect(Collectors.toList());
    }
}
