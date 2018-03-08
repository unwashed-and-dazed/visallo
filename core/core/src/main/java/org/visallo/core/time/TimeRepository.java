package org.visallo.core.time;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

public class TimeRepository {
    public Date getNow() {
        return new Date();
    }

    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    public ZonedDateTime getNowDateTimeUtc() {
        return ZonedDateTime.now(ZoneOffset.UTC);
    }
}
