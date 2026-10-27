package org.praskanth.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PincodesTest {

    @Test
    void takesTheLastSixDigitNumber() {
        assertThat(Pincodes.extract("Asha Rao, 123456 Street, Bengaluru, KA - 560001, India")).isEqualTo("560001");
    }

    @Test
    void returnsNullWhenThereIsNoPincode() {
        assertThat(Pincodes.extract("12 MG Road, Bengaluru")).isNull();
    }

    @Test
    void returnsNullForNullInput() {
        assertThat(Pincodes.extract(null)).isNull();
    }
}
