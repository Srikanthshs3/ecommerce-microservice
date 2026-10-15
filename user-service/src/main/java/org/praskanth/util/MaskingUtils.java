package org.praskanth.util;

public final class MaskingUtils {

    private MaskingUtils() {}

    /** Masks everything except the first {@code keepFirst} and last {@code keepLast} characters. */
    public static String mask(String value, int keepFirst, int keepLast) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= keepFirst + keepLast) {
            return "*".repeat(value.length());
        }
        return value.substring(0, keepFirst)
                + "*".repeat(value.length() - keepFirst - keepLast)
                + value.substring(value.length() - keepLast);
    }

    public static String maskAccountNumber(String accountNumber) {
        return mask(accountNumber, 0, 4);
    }

    public static String maskPan(String pan) {
        return mask(pan, 2, 1);
    }
}
