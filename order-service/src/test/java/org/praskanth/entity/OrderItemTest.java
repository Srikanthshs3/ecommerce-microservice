package org.praskanth.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.praskanth.TestData;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemTest {

    @Test
    void stockId_withoutVariant_isProductId() {
        OrderItem item = TestData.orderItem("p1", null, 1, "10.00");
        assertThat(item.stockId()).isEqualTo("p1");
    }

    @ParameterizedTest
    @CsvSource({"''", "'   '"})
    void stockId_blankVariant_isProductId(String variant) {
        OrderItem item = TestData.orderItem("p1", variant, 1, "10.00");
        assertThat(item.stockId()).isEqualTo("p1");
    }

    @Test
    void stockId_withVariant_isProductColonVariant() {
        OrderItem item = TestData.orderItem("p1", "v9", 1, "10.00");
        assertThat(item.stockId()).isEqualTo("p1:v9");
    }
}
