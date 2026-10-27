package org.praskanth.variant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.praskanth.variant.VariantRules.Input;
import org.praskanth.variant.VariantRules.Merged;
import org.praskanth.variant.VariantRules.Row;
import org.praskanth.variant.VariantRules.RuleViolation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariantRulesTest {

    private static final List<String> COLOUR_SIZE = List.of("Colour", "Size");

    private static Map<String, String> opts(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Input input(String id, Map<String, String> options, String mrp, String selling, Integer stock) {
        return new Input(id, options, mrp == null ? null : new BigDecimal(mrp), selling == null ? null : new BigDecimal(selling), stock);
    }

    private static Row row(String id, Map<String, String> options, String mrp, String selling, boolean active) {
        return new Row(id, options, new BigDecimal(mrp), new BigDecimal(selling), BigDecimal.ZERO, active);
    }

    private static Supplier<String> ids() {
        AtomicInteger counter = new AtomicInteger();
        return () -> "new" + counter.incrementAndGet();
    }

    // ------------------------------------------------------------------------------------ identity

    @Test
    void stockId_withVariant_joinsWithColon() {
        assertThat(VariantRules.stockId("p1", "v1")).isEqualTo("p1:v1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void stockId_blankVariant_returnsProductId(String variantId) {
        assertThat(VariantRules.stockId("p1", variantId)).isEqualTo("p1");
    }

    @Test
    void stockId_nullVariant_returnsProductId() {
        assertThat(VariantRules.stockId("p1", null)).isEqualTo("p1");
    }

    @Test
    void label_joinsValuesWithSlash() {
        assertThat(VariantRules.label(opts("Colour", "Blue", "Size", "M"))).isEqualTo("Blue / M");
    }

    @Test
    void label_null_isEmpty() {
        assertThat(VariantRules.label(null)).isEmpty();
    }

    @Test
    void newId_startsWithVAndHasNineChars() {
        String id = VariantRules.newId();
        assertThat(id).startsWith("v").hasSize(9);
        assertThat(VariantRules.newId()).isNotEqualTo(id);
    }

    // ------------------------------------------------------------------------------ option names

    @Test
    void cleanOptionNames_null_returnsEmpty() {
        assertThat(VariantRules.cleanOptionNames(null)).isEmpty();
    }

    @Test
    void cleanOptionNames_trimsCollapsesSpacesAndSkipsBlanks() {
        List<String> raw = Arrays.asList("  Colour ", " ", null, "Bed   size");
        assertThat(VariantRules.cleanOptionNames(raw)).containsExactly("Colour", "Bed size");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Col$our", "Size!", "A@B", "<b>"})
    void cleanOptionNames_illegalCharacters_throws(String bad) {
        assertThatThrownBy(() -> VariantRules.cleanOptionNames(List.of(bad)))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("is not allowed");
    }

    @Test
    void cleanOptionNames_thirtyChars_isAccepted() {
        String name = "a".repeat(30);
        assertThat(VariantRules.cleanOptionNames(List.of(name))).containsExactly(name);
    }

    @Test
    void cleanOptionNames_thirtyOneChars_throws() {
        String name = "a".repeat(31);
        assertThatThrownBy(() -> VariantRules.cleanOptionNames(List.of(name))).isInstanceOf(RuleViolation.class);
    }

    @Test
    void cleanOptionNames_duplicateIgnoringCase_throws() {
        assertThatThrownBy(() -> VariantRules.cleanOptionNames(List.of("Size", "size")))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("used twice");
    }

    @Test
    void cleanOptionNames_moreThanTwo_throws() {
        assertThatThrownBy(() -> VariantRules.cleanOptionNames(List.of("A", "B", "C")))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("at most 2");
    }

    // ---------------------------------------------------------------------------------- merge

    @Test
    void merge_noOptionNames_throws() {
        assertThatThrownBy(() -> VariantRules.merge(List.of(), List.of(), List.of(), ids()))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("Define the options first");
    }

    @Test
    void merge_nullVariants_throws() {
        assertThatThrownBy(() -> VariantRules.merge(COLOUR_SIZE, List.of(), null, ids()))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("at least one variant");
    }

    @Test
    void merge_emptyVariants_throws() {
        assertThatThrownBy(() -> VariantRules.merge(COLOUR_SIZE, List.of(), List.of(), ids()))
                .isInstanceOf(RuleViolation.class);
    }

    @Test
    void merge_moreThanMaxVariants_throws() {
        List<Input> inputs = new ArrayList<>();
        for (int i = 0; i < VariantRules.MAX_VARIANTS + 1; i++) {
            inputs.add(input(null, opts("Size", "S" + i), "10", "10", 1));
        }
        assertThatThrownBy(() -> VariantRules.merge(List.of("Size"), List.of(), inputs, ids()))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("at most 100 variants");
    }

    @Test
    void merge_newVariants_getGeneratedIdsAndOpeningStock() {
        List<Input> inputs = List.of(
                input(null, opts("Colour", "Blue", "Size", "M"), "1000", "800", 5),
                input(null, opts("Colour", "Blue", "Size", "L"), "1200", "900", null));

        Merged merged = VariantRules.merge(COLOUR_SIZE, null, inputs, ids());

        assertThat(merged.rows()).hasSize(2);
        assertThat(merged.rows()).extracting(Row::id).containsExactly("new1", "new2");
        assertThat(merged.rows()).allMatch(Row::active);
        assertThat(merged.rows().get(0).discountPercent()).isEqualByComparingTo("20.00");
        assertThat(merged.newStock()).containsEntry("new1", 5).containsEntry("new2", 0);
    }

    @Test
    void merge_optionKeysMatchedCaseInsensitivelyAndValuesTrimmed() {
        Input in = input(null, opts(" colour ", "  Dark   Blue ", "SIZE", "M"), "10", "10", 1);

        Merged merged = VariantRules.merge(COLOUR_SIZE, List.of(), List.of(in), ids());

        assertThat(merged.rows().get(0).options()).containsEntry("Colour", "Dark Blue").containsEntry("Size", "M");
    }

    @Test
    void merge_missingOptionValue_throws() {
        Input in = input(null, opts("Colour", "Blue"), "10", "10", 1);
        assertThatThrownBy(() -> VariantRules.merge(COLOUR_SIZE, List.of(), List.of(in), ids()))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("needs a value for 'Size'");
    }

    @Test
    void merge_nullOptionsMap_throws() {
        Input in = input(null, null, "10", "10", 1);
        assertThatThrownBy(() -> VariantRules.merge(List.of("Size"), List.of(), List.of(in), ids()))
                .isInstanceOf(RuleViolation.class);
    }

    @Test
    void merge_valueLongerThanSixtyChars_throws() {
        Input in = input(null, opts("Size", "x".repeat(61)), "10", "10", 1);
        assertThatThrownBy(() -> VariantRules.merge(List.of("Size"), List.of(), List.of(in), ids()))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("at most 60");
    }

    @Test
    void merge_valueOfExactlySixtyChars_isAccepted() {
        Input in = input(null, opts("Size", "x".repeat(60)), "10", "10", 1);
        Merged merged = VariantRules.merge(List.of("Size"), List.of(), List.of(in), ids());
        assertThat(merged.rows()).hasSize(1);
    }

    @Test
    void merge_controlCharacterInValue_throws() {
        Input in = input(null, opts("Size", "M\u0007L"), "10", "10", 1);
        assertThatThrownBy(() -> VariantRules.merge(List.of("Size"), List.of(), List.of(in), ids()))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("control characters");
    }

    @Test
    void merge_sameCombinationTwiceIgnoringCase_throws() {
        List<Input> inputs = List.of(
                input(null, opts("Size", "Medium"), "10", "10", 1),
                input(null, opts("Size", "medium"), "10", "10", 1));
        assertThatThrownBy(() -> VariantRules.merge(List.of("Size"), List.of(), inputs, ids()))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("listed twice");
    }

    @Test
    void merge_missingPrices_throws() {
        Input in = input(null, opts("Size", "M"), null, "10", 1);
        assertThatThrownBy(() -> VariantRules.merge(List.of("Size"), List.of(), List.of(in), ids()))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("needs an MRP and a selling price");
    }

    @Test
    void merge_negativePrice_throws() {
        Input in = input(null, opts("Size", "M"), "10", "-1", 1);
        assertThatThrownBy(() -> VariantRules.merge(List.of("Size"), List.of(), List.of(in), ids()))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void merge_sellingAboveMrp_throws() {
        Input in = input(null, opts("Size", "M"), "10", "10.01", 1);
        assertThatThrownBy(() -> VariantRules.merge(List.of("Size"), List.of(), List.of(in), ids()))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("cannot be higher than its MRP");
    }

    @Test
    void merge_sellingEqualToMrp_isAcceptedWithZeroDiscount() {
        Input in = input(null, opts("Size", "M"), "10", "10", 1);
        Merged merged = VariantRules.merge(List.of("Size"), List.of(), List.of(in), ids());
        assertThat(merged.rows().get(0).discountPercent()).isEqualByComparingTo("0");
    }

    @Test
    void merge_negativeOpeningStock_throws() {
        Input in = input(null, opts("Size", "M"), "10", "10", -1);
        assertThatThrownBy(() -> VariantRules.merge(List.of("Size"), List.of(), List.of(in), ids()))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("Stock for");
    }

    @Test
    void merge_moreThanTwentyValuesForOneOption_throws() {
        List<Input> inputs = new ArrayList<>();
        for (int i = 0; i < VariantRules.MAX_VALUES_PER_OPTION + 1; i++) {
            inputs.add(input(null, opts("Size", "S" + i), "10", "10", 1));
        }
        assertThatThrownBy(() -> VariantRules.merge(List.of("Size"), List.of(), inputs, ids()))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("at most 20 different values");
    }

    @Test
    void merge_exactlyTwentyValues_isAccepted() {
        List<Input> inputs = new ArrayList<>();
        for (int i = 0; i < VariantRules.MAX_VALUES_PER_OPTION; i++) {
            inputs.add(input(null, opts("Size", "S" + i), "10", "10", 1));
        }
        Merged merged = VariantRules.merge(List.of("Size"), List.of(), inputs, ids());
        assertThat(merged.rows()).hasSize(20);
    }

    @Test
    void merge_unknownVariantId_throws() {
        List<Row> stored = List.of(row("v1", opts("Size", "M"), "10", "10", true));
        Input in = input("nope", opts("Size", "M"), "10", "10", 1);
        assertThatThrownBy(() -> VariantRules.merge(List.of("Size"), stored, List.of(in), ids()))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("Unknown variant id");
    }

    @Test
    void merge_existingVariantKeepsIdAndGetsNoNewStock() {
        List<Row> stored = List.of(row("v1", opts("Size", "M"), "10", "10", true));
        Input in = input("v1", opts("Size", "M"), "20", "15", 99);

        Merged merged = VariantRules.merge(List.of("Size"), stored, List.of(in), ids());

        assertThat(merged.rows()).hasSize(1);
        assertThat(merged.rows().get(0).id()).isEqualTo("v1");
        assertThat(merged.rows().get(0).sellingPrice()).isEqualByComparingTo("15");
        assertThat(merged.newStock()).isEmpty();
    }

    @Test
    void merge_sameIdUsedForTwoCombinations_throwsListedTwice() {
        List<Row> stored = List.of(row("v1", opts("Size", "M"), "10", "10", true));
        List<Input> inputs = List.of(
                input("v1", opts("Size", "M"), "10", "10", 1),
                input("v1", opts("Size", "L"), "10", "10", 1));
        assertThatThrownBy(() -> VariantRules.merge(List.of("Size"), stored, inputs, ids()))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("listed twice");
    }

    @Test
    void merge_storedVariantLeftOut_isSwitchedOffNotDeleted() {
        List<Row> stored = List.of(
                row("v1", opts("Size", "M"), "10", "10", true),
                row("v2", opts("Size", "L"), "12", "12", true));
        Input in = input("v1", opts("Size", "M"), "10", "10", null);

        Merged merged = VariantRules.merge(List.of("Size"), stored, List.of(in), ids());

        assertThat(merged.rows()).hasSize(2);
        Row kept = merged.rows().get(0);
        Row off = merged.rows().get(1);
        assertThat(kept.id()).isEqualTo("v1");
        assertThat(kept.active()).isTrue();
        assertThat(off.id()).isEqualTo("v2");
        assertThat(off.active()).isFalse();
    }

    @Test
    void merge_readdingSwitchedOffCombination_reactivatesWithSameIdAndNoNewStock() {
        List<Row> stored = List.of(row("v9", opts("Size", "XL"), "10", "10", false));
        Input in = input(null, opts("Size", "xl"), "10", "10", 50);

        Merged merged = VariantRules.merge(List.of("Size"), stored, List.of(in), ids());

        assertThat(merged.rows()).hasSize(1);
        assertThat(merged.rows().get(0).id()).isEqualTo("v9");
        assertThat(merged.rows().get(0).active()).isTrue();
        assertThat(merged.newStock()).isEmpty();
    }

    @Test
    void merge_blankIdIsTreatedAsNew() {
        Input in = input("  ", opts("Size", "M"), "10", "10", 2);
        Merged merged = VariantRules.merge(List.of("Size"), List.of(), List.of(in), ids());
        assertThat(merged.rows().get(0).id()).isEqualTo("new1");
        assertThat(merged.newStock()).containsEntry("new1", 2);
    }

    // ------------------------------------------------------------------------------ cheapest etc.

    @Test
    void cheapest_picksLowestSellingPriceAmongActive() {
        List<Row> rows = List.of(
                row("a", opts("Size", "S"), "100", "90", true),
                row("b", opts("Size", "M"), "100", "50", true),
                row("c", opts("Size", "L"), "100", "10", false));
        assertThat(VariantRules.cheapest(rows).id()).isEqualTo("b");
    }

    @Test
    void cheapest_tieOnSellingPrice_usesLowerMrp() {
        List<Row> rows = List.of(
                row("a", opts("Size", "S"), "200", "50", true),
                row("b", opts("Size", "M"), "100", "50", true));
        assertThat(VariantRules.cheapest(rows).id()).isEqualTo("b");
    }

    @Test
    void cheapest_noActiveRow_returnsNull() {
        List<Row> rows = List.of(row("a", opts("Size", "S"), "100", "90", false));
        assertThat(VariantRules.cheapest(rows)).isNull();
    }

    @Test
    void discount_variousCases() {
        assertThat(VariantRules.discount(new BigDecimal("1000"), new BigDecimal("750"))).isEqualByComparingTo("25.00");
        assertThat(VariantRules.discount(new BigDecimal("3"), new BigDecimal("2"))).isEqualByComparingTo("33.33");
        assertThat(VariantRules.discount(new BigDecimal("100"), new BigDecimal("100"))).isEqualByComparingTo("0");
        assertThat(VariantRules.discount(null, new BigDecimal("100"))).isEqualByComparingTo("0");
        assertThat(VariantRules.discount(new BigDecimal("100"), null)).isEqualByComparingTo("0");
        assertThat(VariantRules.discount(new BigDecimal("50"), new BigDecimal("100"))).isEqualByComparingTo("0");
    }

    @Test
    void resolveImages_galleryOfFirstOptionValueWins() {
        Row r = row("v1", opts("Colour", "Blue", "Size", "M"), "10", "10", true);
        Map<String, List<String>> galleries = Map.of("blue", List.of("b1.jpg"), "Red", List.of("r1.jpg"));
        List<String> common = List.of("c.jpg");

        assertThat(VariantRules.resolveImages(COLOUR_SIZE, r, galleries, common)).containsExactly("b1.jpg");
    }

    @Test
    void resolveImages_noMatchingGallery_fallsBackToCommon() {
        Row r = row("v1", opts("Colour", "Green", "Size", "M"), "10", "10", true);
        Map<String, List<String>> galleries = Map.of("Blue", List.of("b1.jpg"));
        assertThat(VariantRules.resolveImages(COLOUR_SIZE, r, galleries, List.of("c.jpg"))).containsExactly("c.jpg");
    }

    @Test
    void resolveImages_emptyGallery_fallsBackToCommon() {
        Row r = row("v1", opts("Colour", "Blue", "Size", "M"), "10", "10", true);
        Map<String, List<String>> galleries = Map.of("Blue", List.of());
        assertThat(VariantRules.resolveImages(COLOUR_SIZE, r, galleries, List.of("c.jpg"))).containsExactly("c.jpg");
    }

    @Test
    void resolveImages_nullGalleriesAndNullCommon_returnsEmpty() {
        Row r = row("v1", opts("Colour", "Blue", "Size", "M"), "10", "10", true);
        assertThat(VariantRules.resolveImages(COLOUR_SIZE, r, null, null)).isEmpty();
    }

    @Test
    void resolveImages_noOptionNames_returnsCommon() {
        Row r = row("v1", opts("Colour", "Blue"), "10", "10", true);
        assertThat(VariantRules.resolveImages(List.of(), r, Map.of("Blue", List.of("b.jpg")), List.of("c.jpg")))
                .containsExactly("c.jpg");
    }
}
