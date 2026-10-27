package org.praskanth.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Indian pincode helpers. The order stores the address as one line: "Name, street, city, state - 500001, India". */
public final class Pincodes {

    private static final Pattern PIN = Pattern.compile("(?<![0-9])[1-9][0-9]{5}(?![0-9])");

    private Pincodes() {}

    /** The LAST 6-digit number in the address (street numbers come first, the pincode near the end), or null. */
    public static String extract(String address) {
        if (address == null) {
            return null;
        }
        Matcher m = PIN.matcher(address);
        String last = null;
        while (m.find()) {
            last = m.group();
        }
        return last;
    }
}
