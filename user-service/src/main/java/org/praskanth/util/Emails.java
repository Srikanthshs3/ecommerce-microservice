package org.praskanth.util;

import java.util.Locale;

public final class Emails {

    private Emails() {}

    /** Emails are case-insensitive in practice; normalising avoids duplicate accounts and mismatched Redis keys. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
