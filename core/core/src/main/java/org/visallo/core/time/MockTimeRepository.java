package org.visallo.core.time;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

public class MockTimeRepository extends TimeRepository {
    private Date now;

    public Date getNow() {
        return now;
    }

    public void setNow(Date now) {
        this.now = now;
    }

    public void setNow(ZonedDateTime zonedDateTime) {
        this.now = Date.from(zonedDateTime.toInstant());
    }

    @Override
    public long currentTimeMillis() {
        return getNow().getTime();
    }

    @Override
    public ZonedDateTime getNowDateTimeUtc() {
        return ZonedDateTime.ofInstant(now.toInstant(), ZoneOffset.UTC);
    }
}
