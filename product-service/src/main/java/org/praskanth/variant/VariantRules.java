package org.praskanth.variant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * All the business rules of product variants in one plain-Java class (no Spring, no MongoDB), so they are easy to
 * read and to unit test.
 *
 * <pre>
 *  A product may define 1 or 2 OPTIONS, e.g. ["Colour", "Size"].
 *  A VARIANT is one combination of option values, e.g. {Colour=Blue, Size=M}, and carries its own price and stock.
 *  The stock of a variant lives in inventory-service under the key  productId:variantId  (see {@link #stockId}).
 * </pre>
 *
 * Violations are reported by throwing {@link RuleViolation}; its message is meant to be shown to the seller.
 */
public final class VariantRules {

    public static final int MAX_OPTIONS = 2;
    public static final int MAX_VARIANTS = 100;
    public static final int MAX_VALUES_PER_OPTION = 20;

    private static final Pattern OPTION_NAME = Pattern.compile("^[\\p{L}\\p{N} &/()+-]{1,30}$");

    /** The seller's input is wrong. */
    public static final class RuleViolation extends RuntimeException {
        public RuleViolation(String message) {
            super(message);
        }
    }

    /** One variant as sent by the seller. {@code id} is null for a new one; {@code stock} only counts for a new one. */
    public record Input(String id, Map<String, String> options, BigDecimal mrpPrice, BigDecimal sellingPrice, Integer stock) {
    }

    /** One variant as stored. */
    public record Row(String id, Map<String, String> options, BigDecimal mrpPrice, BigDecimal sellingPrice,
                      BigDecimal discountPercent, boolean active) {
    }

    /** Result of {@link #merge}: the rows to store and which of them must get a stock row in inventory-service. */
    public record Merged(List<Row> rows, Map<String, Integer> newStock) {
    }

    private VariantRules() {
    }

    // ------------------------------------------------------------------------------------------ identity

    /** The key under which inventory-service keeps this variant's stock (the order flow uses the same key). */
    public static String stockId(String productId, String variantId) {
        return variantId == null || variantId.isBlank() ? productId : productId + ":" + variantId;
    }

    /** "Blue / M" - what the customer sees under the product name in the cart and in orders. */
    public static String label(Map<String, String> options) {
        return options == null ? "" : String.join(" / ", options.values());
    }

    public static String newId() {
        return "v" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ---------------------------------------------------------------------------------------- option names

    /** null / empty = a simple product without options. */
    public static List<String> cleanOptionNames(List<String> raw) {
        List<String> names = new ArrayList<>();
        if (raw != null) {
            for (String name : raw) {
                String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ");
                if (clean.isEmpty()) {
                    continue;
                }
                if (!OPTION_NAME.matcher(clean).matches()) {
                    throw new RuleViolation("Option name '" + clean + "' is not allowed: use up to 30 letters, digits or spaces (for example Colour, Size, Bed size).");
                }
                for (String other : names) {
                    if (other.equalsIgnoreCase(clean)) {
                        throw new RuleViolation("Option name '" + clean + "' is used twice.");
                    }
                }
                names.add(clean);
            }
        }
        if (names.size() > MAX_OPTIONS) {
            throw new RuleViolation("A product can have at most " + MAX_OPTIONS + " options (for example Colour and Size).");
        }
        return names;
    }

    // -------------------------------------------------------------------------------------------- merging

    /**
     * Validates what the seller sent and merges it with what is stored.
     * <ul>
     *   <li>A variant keeps its id (and therefore its stock row) as long as the seller keeps it in the list.</li>
     *   <li>A combination that is new gets a new id and a stock row starting at the entered stock.</li>
     *   <li>A stored variant missing from the list is NOT deleted: it is switched off (active=false), so carts and
     *       past orders that point to it stay valid. Adding the same combination again switches it back on.</li>
     * </ul>
     */
    public static Merged merge(List<String> optionNames, List<Row> stored, List<Input> submitted, Supplier<String> idGenerator) {
        if (optionNames.isEmpty()) {
            throw new RuleViolation("Define the options first (for example Colour and Size).");
        }
        if (submitted == null || submitted.isEmpty()) {
            throw new RuleViolation("Add at least one variant.");
        }
        if (submitted.size() > MAX_VARIANTS) {
            throw new RuleViolation("A product can have at most " + MAX_VARIANTS + " variants.");
        }

        Map<String, Row> storedById = new HashMap<>();
        Map<String, Row> storedByCombo = new HashMap<>();
        for (Row row : stored == null ? List.<Row>of() : stored) {
            storedById.put(row.id(), row);
            storedByCombo.put(comboKey(row.options()), row);
        }

        List<Row> result = new ArrayList<>();
        Map<String, Integer> newStock = new LinkedHashMap<>();
        Set<String> seenCombos = new LinkedHashSet<>();
        Set<String> usedIds = new LinkedHashSet<>();
        List<Set<String>> valuesPerOption = new ArrayList<>();
        for (int i = 0; i < optionNames.size(); i++) {
            valuesPerOption.add(new LinkedHashSet<>());
        }

        for (Input in : submitted) {
            Map<String, String> options = normaliseOptions(optionNames, in.options());
            String label = label(options);
            String combo = comboKey(options);
            if (!seenCombos.add(combo)) {
                throw new RuleViolation("'" + label + "' is listed twice.");
            }
            checkPrices(label, in.mrpPrice(), in.sellingPrice());
            for (int i = 0; i < optionNames.size(); i++) {
                valuesPerOption.get(i).add(options.get(optionNames.get(i)).toLowerCase(Locale.ROOT));
                if (valuesPerOption.get(i).size() > MAX_VALUES_PER_OPTION) {
                    throw new RuleViolation("'" + optionNames.get(i) + "' can have at most " + MAX_VALUES_PER_OPTION + " different values.");
                }
            }

            String id;
            boolean isNew = false;
            if (in.id() != null && !in.id().isBlank()) {
                if (!storedById.containsKey(in.id())) {
                    throw new RuleViolation("Unknown variant id '" + in.id() + "'. Reload the product and try again.");
                }
                id = in.id();
            } else if (storedByCombo.containsKey(combo)) {
                id = storedByCombo.get(combo).id(); // same combination as a switched-off variant: bring it back
            } else {
                id = idGenerator.get();
                isNew = true;
            }
            if (!usedIds.add(id)) {
                throw new RuleViolation("'" + label + "' is listed twice.");
            }
            result.add(new Row(id, options, in.mrpPrice(), in.sellingPrice(), discount(in.mrpPrice(), in.sellingPrice()), true));
            if (isNew) {
                int qty = in.stock() == null ? 0 : in.stock();
                if (qty < 0) {
                    throw new RuleViolation("Stock for '" + label + "' cannot be negative.");
                }
                newStock.put(id, qty);
            }
        }

        // stored variants that were left out are kept but switched off
        for (Row row : stored == null ? List.<Row>of() : stored) {
            if (!usedIds.contains(row.id())) {
                result.add(new Row(row.id(), row.options(), row.mrpPrice(), row.sellingPrice(), row.discountPercent(), false));
            }
        }
        return new Merged(result, newStock);
    }

    // --------------------------------------------------------------------------------------- derived data

    /** The variant shown as "From Rs X" on cards: the cheapest active one. Null when none is active. */
    public static Row cheapest(List<Row> rows) {
        return rows.stream().filter(Row::active)
                .min(Comparator.comparing(Row::sellingPrice).thenComparing(Row::mrpPrice))
                .orElse(null);
    }

    /**
     * Pictures of one variant: the gallery of its FIRST option's value (e.g. all "Blue" variants share the blue
     * pictures, whatever the size), otherwise the product's common pictures.
     */
    public static List<String> resolveImages(List<String> optionNames, Row row, Map<String, List<String>> galleries, List<String> common) {
        if (!optionNames.isEmpty() && galleries != null) {
            String wanted = row.options().get(optionNames.get(0));
            for (Map.Entry<String, List<String>> e : galleries.entrySet()) {
                if (wanted != null && e.getKey().equalsIgnoreCase(wanted) && e.getValue() != null && !e.getValue().isEmpty()) {
                    return e.getValue();
                }
            }
        }
        return common == null ? List.of() : common;
    }

    public static BigDecimal discount(BigDecimal mrp, BigDecimal selling) {
        if (mrp == null || selling == null || mrp.compareTo(selling) <= 0) {
            return BigDecimal.ZERO;
        }
        return mrp.subtract(selling).divide(mrp, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    // ----------------------------------------------------------------------------------------- internals

    private static Map<String, String> normaliseOptions(List<String> optionNames, Map<String, String> given) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : optionNames) {
            String value = null;
            if (given != null) {
                for (Map.Entry<String, String> e : given.entrySet()) {
                    if (e.getKey() != null && e.getKey().trim().equalsIgnoreCase(name)) {
                        value = e.getValue();
                    }
                }
            }
            String clean = value == null ? "" : value.trim().replaceAll("\\s+", " ");
            if (clean.isEmpty()) {
                throw new RuleViolation("Every variant needs a value for '" + name + "'.");
            }
            if (clean.length() > 60) {
                throw new RuleViolation("'" + clean.substring(0, 20) + "...' is too long: a value can have at most 60 characters.");
            }
            if (clean.chars().anyMatch(Character::isISOControl)) {
                throw new RuleViolation("Option values cannot contain control characters.");
            }
            out.put(name, clean);
        }
        return out;
    }

    private static void checkPrices(String label, BigDecimal mrp, BigDecimal selling) {
        if (mrp == null || selling == null) {
            throw new RuleViolation("'" + label + "' needs an MRP and a selling price.");
        }
        if (mrp.signum() < 0 || selling.signum() < 0) {
            throw new RuleViolation("Prices for '" + label + "' cannot be negative.");
        }
        if (selling.compareTo(mrp) > 0) {
            throw new RuleViolation("The selling price of '" + label + "' cannot be higher than its MRP.");
        }
    }

    private static String comboKey(Map<String, String> options) {
        StringBuilder key = new StringBuilder();
        for (String value : options.values()) {
            key.append(value.trim().toLowerCase(Locale.ROOT)).append('\u0001');
        }
        return key.toString();
    }
}
