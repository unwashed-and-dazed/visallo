package org.visallo.core.model.properties.types;

import org.junit.Before;
import org.junit.Test;
import org.vertexium.Authorizations;
import org.vertexium.Element;
import org.vertexium.Vertex;
import org.vertexium.Visibility;
import org.visallo.core.model.graph.GraphUpdateContext;
import org.visallo.core.model.workQueue.Priority;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloInMemoryTestBase;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.Assert.assertEquals;

public class DateVisalloPropertyTest extends VisalloInMemoryTestBase {
    private User user;
    private Authorizations authorizations;
    private PropertyMetadata metadata;
    private DateVisalloProperty prop;

    @Before
    public void before() throws Exception {
        super.before();
        user = getUserRepository().getSystemUser();
        authorizations = getAuthorizationRepository().getGraphAuthorizations(user);
        metadata = new PropertyMetadata(user, new VisibilityJson(""), new Visibility(""));
        prop = new DateVisalloProperty("name");
    }

    @Test
    public void testUpdatePropertyIfValueIsNewer_newer() {
        Date oldValue = Date.from(ZonedDateTime.of(2017, 2, 6, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        Date newValue = Date.from(ZonedDateTime.of(2017, 2, 7, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        Date expectedValue = Date.from(ZonedDateTime.of(2017, 2, 7, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        testUpdatePropertyIfValueIsNewer(oldValue, newValue, expectedValue);
    }

    @Test
    public void testUpdatePropertyIfValueIsNewer_older() {
        Date oldValue = Date.from(ZonedDateTime.of(2017, 2, 6, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        Date newValue = Date.from(ZonedDateTime.of(2017, 2, 5, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        Date expectedValue = Date.from(ZonedDateTime.of(2017, 2, 6, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        testUpdatePropertyIfValueIsNewer(oldValue, newValue, expectedValue);
    }

    @Test
    public void testUpdatePropertyIfValueIsNewer_newValue() {
        Date oldValue = null;
        Date newValue = Date.from(ZonedDateTime.of(2017, 2, 5, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        Date expectedValue = Date.from(ZonedDateTime.of(2017, 2, 5, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        testUpdatePropertyIfValueIsNewer(oldValue, newValue, expectedValue);
    }

    private void testUpdatePropertyIfValueIsNewer(Date oldValue, Date newValue, Date expectedValue) {
        testUpdateProperty(prop, expectedValue, oldValue, elemCtx ->
                prop.updatePropertyIfValueIsNewer(elemCtx, "key", newValue, metadata));
    }

    @Test
    public void testUpdatePropertyIfValueIsOlder_newer() {
        Date oldValue = Date.from(ZonedDateTime.of(2017, 2, 6, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        Date newValue = Date.from(ZonedDateTime.of(2017, 2, 7, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        Date expectedValue = Date.from(ZonedDateTime.of(2017, 2, 6, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        testUpdatePropertyIfValueIsOlder(oldValue, newValue, expectedValue);
    }

    @Test
    public void testUpdatePropertyIfValueIsOlder_older() {
        Date oldValue = Date.from(ZonedDateTime.of(2017, 2, 6, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        Date newValue = Date.from(ZonedDateTime.of(2017, 2, 5, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        Date expectedValue = Date.from(ZonedDateTime.of(2017, 2, 5, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        testUpdatePropertyIfValueIsOlder(oldValue, newValue, expectedValue);
    }

    @Test
    public void testUpdatePropertyIfValueIsOlder_newValue() {
        Date oldValue = null;
        Date newValue = Date.from(ZonedDateTime.of(2017, 2, 5, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        Date expectedValue = Date.from(ZonedDateTime.of(2017, 2, 5, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant());
        testUpdatePropertyIfValueIsOlder(oldValue, newValue, expectedValue);
    }

    private void testUpdatePropertyIfValueIsOlder(Date oldValue, Date newValue, Date expectedValue) {
        testUpdateProperty(prop, expectedValue, oldValue, elemCtx ->
                prop.updatePropertyIfValueIsOlder(elemCtx, "key", newValue, metadata));
    }

    private void testUpdateProperty(DateVisalloProperty prop, Date expectedValue, Date oldValue, GraphUpdateContext.Update<Element> update) {
        Vertex v = getGraph().addVertex("v1", new Visibility(""), authorizations);
        if (oldValue != null) {
            prop.addPropertyValue(v, "key", oldValue, new Visibility(""), authorizations);
        }

        v = getGraph().getVertex("v1", authorizations);
        try (GraphUpdateContext ctx = getGraphRepository().beginGraphUpdate(Priority.NORMAL, user, authorizations)) {
            ctx.update(v, update);
        }

        v = getGraph().getVertex("v1", authorizations);
        Date value = prop.getPropertyValue(v, "key");
        assertEquals(expectedValue, value);
    }
}
