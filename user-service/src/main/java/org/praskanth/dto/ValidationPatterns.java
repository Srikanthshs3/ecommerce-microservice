package org.praskanth.dto;

/** Central place for input-validation regexes so customer and seller DTOs stay consistent. */
public final class ValidationPatterns {

    private ValidationPatterns() {}

    /** 8-64 chars, at least one letter and one digit. (64 keeps us safely under BCrypt's 72-byte limit.) */
    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";
    public static final String PASSWORD_MESSAGE = "Password must be 8-64 characters and contain at least one letter and one digit";

    /** E.164-ish: optional +, 10-15 digits. */
    public static final String PHONE = "^\\+?[0-9]{10,15}$";

    /** Indian GSTIN: 2 state digits + PAN(10) + entity code + 'Z' + checksum. Case-insensitive, normalised in service. */
    public static final String GSTIN = "(?i)^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";
    public static final String PAN = "(?i)^[A-Z]{5}[0-9]{4}[A-Z]$";
    public static final String IFSC = "(?i)^[A-Z]{4}0[A-Z0-9]{6}$";
    public static final String BANK_ACCOUNT = "^[0-9]{9,18}$";
}
