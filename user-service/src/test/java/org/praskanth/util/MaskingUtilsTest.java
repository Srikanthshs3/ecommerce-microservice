package org.praskanth.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "1234567890, 2, 3, 12*****890",
            "1234567890, 0, 4, ******7890",
            "abcde, 2, 3, *****",
            "abcd, 2, 3, ****"
    })
    void mask_masksMiddle_orEverythingWhenTooShort(String value, int first, int last, String expected) {
        assertThat(MaskingUtils.mask(value, first, last)).isEqualTo(expected);
    }

    @Test
    void mask_boundaryOneCharMasked() {
        assertThat(MaskingUtils.mask("abcdef", 2, 3)).isEqualTo("ab*def");
        assertThat(MaskingUtils.mask("abcdefg", 2, 3)).isEqualTo("ab**efg");
    }

    @Test
    void mask_nullAndEmpty_returnedAsIs() {
        assertThat(MaskingUtils.mask(null, 1, 1)).isNull();
        assertThat(MaskingUtils.mask("", 1, 1)).isEmpty();
    }

    @Test
    void maskAccountNumber_keepsLastFour() {
        assertThat(MaskingUtils.maskAccountNumber("123456789012")).isEqualTo("********9012");
    }

    @Test
    void maskPan_keepsFirstTwoAndLastOne() {
        assertThat(MaskingUtils.maskPan("ABCDE1234F")).isEqualTo("AB*******F");
    }
}
