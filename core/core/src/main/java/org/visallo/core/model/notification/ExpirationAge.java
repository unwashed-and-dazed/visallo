package org.visallo.core.model.notification;

import org.visallo.core.exception.VisalloException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpirationAge {
    private int amount;
    private ExpirationAgeUnit expirationAgeUnit;

    public ExpirationAge(int amount, ExpirationAgeUnit expirationAgeUnit) {
        this.amount = amount;
        this.expirationAgeUnit = expirationAgeUnit;
    }

    public int getAmount() {
        return amount;
    }

    public ExpirationAgeUnit getExpirationAgeUnit() {
        return expirationAgeUnit;
    }

    @Override
    public String toString() {
        return getAmount() + getExpirationAgeUnit().getAbbreviation();
    }

    public static ExpirationAge parse(String value) {
        Matcher m = Pattern.compile("([0-9]+)(.)").matcher(value);
        if (!m.matches()) {
            throw new VisalloException("Could not parse " + ExpirationAge.class.getSimpleName() + ": " + value);
        }
        return new ExpirationAge(
                Integer.parseInt(m.group(1)),
                ExpirationAgeUnit.parse(m.group(2))
        );
    }
}
