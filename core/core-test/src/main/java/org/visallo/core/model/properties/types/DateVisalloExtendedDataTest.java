package org.visallo.core.model.properties.types;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.vertexium.ExtendedDataRow;
import org.visallo.core.util.VisalloInMemoryTestBase;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DateVisalloExtendedDataTest extends VisalloInMemoryTestBase {
    private DateVisalloExtendedData property;

    @Mock
    private ExtendedDataRow row;

    @Before
    public void before() throws Exception {
        super.before();
        property = new DateVisalloExtendedData("table1", "prop1");
    }

    @Test
    public void testGetValueDateTimeUtcNull() {
        when(row.getPropertyValue("prop1")).thenReturn(null);
        ZonedDateTime value = property.getValueDateTimeUtc(row);
        assertNull(value);
    }

    @Test
    public void testGetValueDateTimeUtc() {
        ZonedDateTime time = ZonedDateTime.of(2018, 1, 18, 21, 2, 10, 0, ZoneOffset.UTC);
        Date date = Date.from(time.toInstant());
        when(row.getPropertyValue("prop1")).thenReturn(date);
        ZonedDateTime value = property.getValueDateTimeUtc(row);
        assertEquals(time, value);
    }
}
