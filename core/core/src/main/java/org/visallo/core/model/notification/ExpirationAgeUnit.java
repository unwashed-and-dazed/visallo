package org.visallo.core.model.notification;

import org.visallo.core.exception.VisalloException;

import java.util.Calendar;

public enum ExpirationAgeUnit {
    SECOND(Calendar.SECOND, "SECOND", "SECOND", "s"),
    MINUTE(Calendar.MINUTE, "MINUTE", "MINUTE", "m"),
    HOUR(Calendar.HOUR_OF_DAY, "HOUR", "HOUR", "h"),
    DAY(Calendar.DAY_OF_WEEK, "DAY", "DAY", "d");

    private final int calendarUnit;
    private final String mysqlInterval;
    private final String h2unit;
    private final String abbreviation;

    private ExpirationAgeUnit(int calendarUnit, String mysqlInterval, String h2unit, String abbreviation) {
        this.calendarUnit = calendarUnit;
        this.mysqlInterval = mysqlInterval;
        this.h2unit = h2unit;
        this.abbreviation = abbreviation;
    }

    public int getCalendarUnit() {
        return calendarUnit;
    }

    public String getMysqlInterval() {
        return mysqlInterval;
    }

    public String getH2unit() {
        return h2unit;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public static ExpirationAgeUnit parse(String value) {
        for (ExpirationAgeUnit u : ExpirationAgeUnit.values()) {
            if (u.getAbbreviation().equals(value)) {
                return u;
            }
        }
        throw new VisalloException("could not parse " + ExpirationAgeUnit.class.getSimpleName() + ": " + value);
    }
}
