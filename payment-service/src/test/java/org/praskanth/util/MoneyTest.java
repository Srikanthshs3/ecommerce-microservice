package org.praskanth.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @ParameterizedTest(name = "{0} -> {1} minor units")
    @CsvSource({
            "499.50, 49950",
            "0, 0",
            "0.01, 1",
            "1, 100",
            "0.005, 1",
            "0.004, 0",
            "10.125, 1013",
            "50000.00, 5000000",
            "-1.50, -150"
    })
    @DisplayName("toMinor converts to the smallest currency unit, rounding half up")
    void toMinor_variousAmounts_convertsToMinorUnits(String amount, long expected) {
        // Given a decimal amount / When converted / Then the whole number of minor units is returned
        assertThat(Money.toMinor(new BigDecimal(amount))).isEqualTo(expected);
    }

    @Test
    @DisplayName("toMinor fails loudly when the value does not fit in a long")
    void toMinor_overflow_throwsArithmeticException() {
        BigDecimal huge = new BigDecimal("99999999999999999999");

        assertThatThrownBy(() -> Money.toMinor(huge)).isInstanceOf(ArithmeticException.class);
    }
}
