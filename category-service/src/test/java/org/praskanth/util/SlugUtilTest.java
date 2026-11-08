package org.praskanth.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilTest {

    static Stream<Arguments> slugCases() {
        return Stream.of(
                Arguments.of("Men's", "mens"),
                Arguments.of("T-Shirts & Polos", "t-shirts-and-polos"),
                Arguments.of("U.S. Polo Assn.", "us-polo-assn"),
                Arguments.of("L'Oréal", "loreal"),
                Arguments.of("Levi’s", "levis"),
                Arguments.of("Mobiles & Computers", "mobiles-and-computers"),
                Arguments.of("H&M", "h-and-m"),
                Arguments.of("  spaced   out  ", "spaced-out"),
                Arguments.of("--abc--", "abc"),
                Arguments.of("A  B", "a-b"),
                Arguments.of("ÀÉÎ", "aei"),
                Arguments.of("Already-Slugged-123", "already-slugged-123"),
                Arguments.of("Macroman M-Series", "macroman-m-series"),
                Arguments.of("Story@Home", "story-home"),
                Arguments.of("W", "w"));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
    @MethodSource("slugCases")
    @DisplayName("slugify produces URL-safe slugs")
    void slugify_variousInputs_returnsExpectedSlug(String input, String expected) {
        // When
        String slug = SlugUtil.slugify(input);

        // Then
        assertThat(slug).isEqualTo(expected);
    }

    @Test
    @DisplayName("slugify returns empty string for null")
    void slugify_null_returnsEmpty() {
        assertThat(SlugUtil.slugify(null)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("emptyResultInputs")
    @DisplayName("slugify returns empty string when nothing alphanumeric remains")
    void slugify_noAlphanumerics_returnsEmpty(String input) {
        assertThat(SlugUtil.slugify(input)).isEmpty();
    }

    static Stream<String> emptyResultInputs() {
        return Stream.of("", "   ", "!!!", "---", "'.`");
    }
}
