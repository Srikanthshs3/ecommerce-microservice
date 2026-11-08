package org.praskanth.exception;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/**
 * Every sentence an end user can read because something went wrong lives here (or in the exception that carried it),
 * never in a controller. Plain Java on purpose: no Spring, so it is unit-tested and also usable by the reactive gateway.
 *
 * Rules the whole platform follows:
 *  - no HTTP status codes, class names, SQL, host names or service names in a message;
 *  - say what happened in everyday words and, when possible, what the person can do next;
 *  - for OUR faults (5xx) say so, and give a short reference the support team can look up in the logs.
 */
public final class ErrorMessages {

    private static final Map<String, String> BY_CODE = Map.ofEntries(
            Map.entry("BAD_REQUEST", "Something in your request isn't right. Please check it and try again."),
            Map.entry("VALIDATION_ERROR", "Please check the information you entered and try again."),
            Map.entry("MALFORMED_REQUEST", "We couldn't read the information you sent. Please check it and try again."),
            Map.entry("UNAUTHORIZED", "Please sign in to continue."),
            Map.entry("FORBIDDEN", "You don't have permission to do that."),
            Map.entry("NOT_FOUND", "We couldn't find what you were looking for."),
            Map.entry("METHOD_NOT_ALLOWED", "That action isn't available here."),
            Map.entry("NOT_ACCEPTABLE", "We can't show this in the format that was requested."),
            Map.entry("CONFLICT", "That can't be done right now because it clashes with something that already exists."),
            Map.entry("DATA_CONFLICT", "That already exists, or it is being used elsewhere, so the change couldn't be made."),
            Map.entry("CONCURRENT_UPDATE", "Someone else changed this at the same moment. Please refresh and try again."),
            Map.entry("PAYLOAD_TOO_LARGE", "The file is too large. Please choose a smaller one and try again."),
            Map.entry("UNSUPPORTED_MEDIA_TYPE", "That type of file or format isn't supported."),
            Map.entry("TOO_MANY_REQUESTS", "You're going a little too fast. Please wait a moment and try again."),
            Map.entry("SERVICE_UNAVAILABLE", "This part of the shop is temporarily unavailable. Please try again in a few moments."),
            Map.entry("DATABASE_UNAVAILABLE", "We're having trouble loading and saving data right now. Please try again in a few moments."),
            Map.entry("UPSTREAM_ERROR", "We couldn't finish that because a connected service had a problem. Please try again shortly."),
            Map.entry("GATEWAY_TIMEOUT", "This is taking longer than expected. Please try again in a moment."),
            Map.entry("INTERNAL_ERROR", "Something went wrong on our side. Please try again in a moment.")
    );

    private static final String DEFAULT = BY_CODE.get("INTERNAL_ERROR");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I: read aloud on the phone
    private static final SecureRandom RANDOM = new SecureRandom();

    private ErrorMessages() {
    }

    /** The default sentence for an error code ({@code NOT_FOUND}, {@code SERVICE_UNAVAILABLE}, ...). */
    public static String forCode(String code) {
        return BY_CODE.getOrDefault(code, DEFAULT);
    }

    /** The default sentence for an HTTP status, for answers that carry no message of their own. */
    public static String forStatus(int status) {
        return switch (status) {
            case 400 -> forCode("BAD_REQUEST");
            case 401 -> forCode("UNAUTHORIZED");
            case 403 -> forCode("FORBIDDEN");
            case 404 -> forCode("NOT_FOUND");
            case 405 -> forCode("METHOD_NOT_ALLOWED");
            case 406 -> forCode("NOT_ACCEPTABLE");
            case 408, 504 -> forCode("GATEWAY_TIMEOUT");
            case 409 -> forCode("CONFLICT");
            case 413 -> forCode("PAYLOAD_TOO_LARGE");
            case 415 -> forCode("UNSUPPORTED_MEDIA_TYPE");
            case 422 -> forCode("VALIDATION_ERROR");
            case 429 -> forCode("TOO_MANY_REQUESTS");
            case 502 -> forCode("UPSTREAM_ERROR");
            case 503 -> forCode("SERVICE_UNAVAILABLE");
            default -> status >= 500 ? forCode("INTERNAL_ERROR") : forCode("BAD_REQUEST");
        };
    }

    /** Short code the customer can quote to support; the same value is written next to the stack trace in the log. */
    public static String newReference() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** "Something went wrong on our side ... (Reference: K7M2Q9XA)". */
    public static String withReference(String message, String reference) {
        return message + " If it keeps happening, contact support and quote reference " + reference + ".";
    }

    // ---------------------------------------------------------------------------------------- validation text

    /** "firstName" -> "First name", "address.zipCode" -> "Zip code", "items[0].qty" -> "Qty". */
    public static String humanizeField(String field) {
        if (field == null || field.isBlank()) {
            return "This field";
        }
        String leaf = field.contains(".") ? field.substring(field.lastIndexOf('.') + 1) : field;
        leaf = leaf.replaceAll("\\[\\d+]", "").replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < leaf.length(); i++) {
            char c = leaf.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && !Character.isUpperCase(leaf.charAt(i - 1))) {
                sb.append(' ');
            }
            sb.append(i == 0 ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        String text = sb.toString().trim();
        return text.isEmpty() ? "This field" : text;
    }

    /**
     * Turns a bean-validation result into a sentence a person understands.
     * "must not be blank" on "firstName" -> "First name must not be blank".
     * A message the developer already wrote as a full sentence ("Password must have at least 8 characters") is kept.
     */
    public static String validationSentence(String field, String message) {
        String m = message == null || message.isBlank() ? "is not valid" : message.trim();
        if (Character.isUpperCase(m.charAt(0))) {
            return m;
        }
        return humanizeField(field) + " " + m;
    }

    /** The one-line message for a failed validation: the sentence itself when there is one problem, else a short list. */
    public static String validationSummary(List<String> sentences) {
        if (sentences == null || sentences.isEmpty()) {
            return forCode("VALIDATION_ERROR");
        }
        if (sentences.size() == 1) {
            return sentences.get(0);
        }
        int shown = Math.min(3, sentences.size());
        String head = String.join("; ", sentences.subList(0, shown));
        return sentences.size() > shown
                ? head + "; and " + (sentences.size() - shown) + " more."
                : head + ".";
    }
}
