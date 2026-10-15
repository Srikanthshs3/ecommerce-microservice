package org.praskanth.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * URL-safe slugs for category names. Pure Java (no Spring) so it is trivially unit-testable.
 *
 *   "Men's"            -> "mens"
 *   "T-Shirts & Polos" -> "t-shirts-and-polos"
 *   "U.S. Polo Assn."  -> "us-polo-assn"
 *   "L'Oréal"          -> "loreal"
 */
public final class SlugUtil {

    private SlugUtil() {
    }

    public static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String s = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}+", ""); // strip accents
        s = s.toLowerCase(Locale.ROOT);
        s = s.replace("&", " and ");
        s = s.replaceAll("['\u2019`.]", "");        // apostrophes and dots vanish: Men's -> mens, U.S. -> us
        s = s.replaceAll("[^a-z0-9]+", "-");        // everything else becomes a hyphen
        s = s.replaceAll("^-+|-+$", "");            // no leading / trailing hyphen
        return s;
    }
}
