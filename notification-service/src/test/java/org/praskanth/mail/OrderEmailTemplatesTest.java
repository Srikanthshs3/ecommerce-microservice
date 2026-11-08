package org.praskanth.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.praskanth.events.OrderNotificationEvent;
import org.praskanth.events.OrderNotificationItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEmailTemplatesTest {

    private static final String URL = "http://localhost:5173/orders";

    private static OrderNotificationEvent event(String stage) {
        OrderNotificationItem item = new OrderNotificationItem();
        item.setProductName("Cotton Shirt");
        item.setVariantLabel("Blue / M");
        item.setQuantity(2);
        item.setUnitPrice("400.00");

        OrderNotificationEvent e = new OrderNotificationEvent();
        e.setEventId("e-1");
        e.setOrderId(42L);
        e.setUserId(5L);
        e.setStage(stage);
        e.setCustomerEmail("asha@example.com");
        e.setCustomerName("Asha Rao");
        e.setRecipientName("Asha");
        e.setShippingAddress("12 MG Road, Bengaluru");
        e.setTotalAmount("800.00");
        e.setItems(List.of(item));
        e.setOccurredAt(java.time.Instant.parse("2026-10-22T10:00:00Z"));
        return e;
    }

    @ParameterizedTest
    @CsvSource({
            "ORDER_PLACED,        is confirmed",
            "PACKED,              is packed",
            "OUT_FOR_DELIVERY,    is on its way",
            "DELIVERED,           was delivered",
            "CANCELLED,           was cancelled",
            "OUT_OF_STOCK,        out of stock"
    })
    void everyStageHasItsOwnSubjectWithTheOrderNumber(String stage, String wording) {
        OrderEmailTemplates.Mail mail = OrderEmailTemplates.build(event(stage), URL);

        assertThat(mail).isNotNull();
        assertThat(mail.subject()).contains("#42").contains(wording);
    }

    @Test
    void bodyListsItemsTotalAddressAndLink() {
        OrderEmailTemplates.Mail mail = OrderEmailTemplates.build(event("ORDER_PLACED"), URL);

        assertThat(mail.html()).contains("Cotton Shirt (Blue / M)").contains("800.00").contains("12 MG Road, Bengaluru")
                .contains(URL).contains("Thanks, Asha!");
        assertThat(mail.text()).contains("Cotton Shirt (Blue / M) x2").contains("Total: Rs 800.00").contains(URL);
    }

    @Test
    void cancelledMentionsTheReason() {
        OrderNotificationEvent e = event("CANCELLED");
        e.setReason("Cancelled by seller");

        OrderEmailTemplates.Mail mail = OrderEmailTemplates.build(e, URL);

        assertThat(mail.html()).contains("Reason: Cancelled by seller.");
        assertThat(mail.text()).contains("Reason: Cancelled by seller.");
    }

    @Test
    void outOfStockSaysNoPaymentWasTaken() {
        assertThat(OrderEmailTemplates.build(event("OUT_OF_STOCK"), URL).text()).contains("No payment was taken");
    }

    @Test
    void customerTextIsHtmlEscaped() {
        OrderNotificationEvent e = event("PACKED");
        e.setShippingAddress("<script>alert(1)</script> & co");
        e.setCustomerName("<b>Eve</b>");

        String html = OrderEmailTemplates.build(e, URL).html();

        assertThat(html).doesNotContain("<script>").doesNotContain("<b>Eve");
        assertThat(html).contains("&lt;script&gt;").contains("&amp; co");
    }

    @Test
    void missingNameFallsBackToThere() {
        OrderNotificationEvent e = event("DELIVERED");
        e.setCustomerName(null);

        assertThat(OrderEmailTemplates.build(e, URL).text()).contains("there");
    }

    @Test
    void noItemsOrAddressOrLink_stillProducesAValidMail() {
        OrderNotificationEvent e = event("PACKED");
        e.setItems(List.of());
        e.setShippingAddress(null);

        OrderEmailTemplates.Mail mail = OrderEmailTemplates.build(e, null);

        assertThat(mail.html()).doesNotContain("<table").doesNotContain("View my orders");
        assertThat(mail.text()).doesNotContain("Total:");
    }

    @Test
    void unknownStage_returnsNull() {
        assertThat(OrderEmailTemplates.build(event("SOMETHING_NEW"), URL)).isNull();
    }
}
