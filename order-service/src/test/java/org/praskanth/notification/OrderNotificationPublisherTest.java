package org.praskanth.notification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.TestData;
import org.praskanth.entity.Order;
import org.praskanth.entity.OrderStatus;
import org.praskanth.outbox.OrderNotificationFields;
import org.praskanth.outbox.OutboxEventWriter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderNotificationPublisherTest {

    @Mock OutboxEventWriter outboxEventWriter;
    @Mock PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private OrderNotificationPublisher publisher() {
        return new OrderNotificationPublisher(outboxEventWriter, transactionManager);
    }

    private Order orderWithEmail(String email) {
        Order order = TestData.order(10L, 5L, 7L, OrderStatus.CONFIRMED);
        order.setCustomerEmail(email);
        order.setCustomerName("Asha Rao");
        order.setRecipientName("Asha");
        order.setShippingAddress("12 MG Road, Bengaluru");
        order.setTotalAmount(new BigDecimal("799.5"));
        order.getItems().add(TestData.orderItem("P1", "V1", 2, "400"));
        return order;
    }

    @Test
    void publishesAnOutboxRowWithEverythingTheEmailNeeds() {
        publisher().publish(orderWithEmail("asha@example.com"), OrderStage.PACKED, null);

        ArgumentCaptor<Object> fields = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventWriter).write(eq("OrderNotificationEvent"), eq("order.notification"), eq("10"), fields.capture());
        OrderNotificationFields f = (OrderNotificationFields) fields.getValue();
        assertThat(f.orderId()).isEqualTo(10L);
        assertThat(f.userId()).isEqualTo(5L);
        assertThat(f.stage()).isEqualTo("PACKED");
        assertThat(f.customerEmail()).isEqualTo("asha@example.com");
        assertThat(f.customerName()).isEqualTo("Asha Rao");
        assertThat(f.totalAmount()).isEqualTo("799.50");
        assertThat(f.items()).hasSize(1);
        assertThat(f.items().get(0).quantity()).isEqualTo(2);
        assertThat(f.items().get(0).unitPrice()).isEqualTo("400.00");
        assertThat(f.occurredAt()).isNotNull();
    }

    @Test
    void carriesTheCancellationReason() {
        publisher().publish(orderWithEmail("asha@example.com"), OrderStage.CANCELLED, "Cancelled by seller");

        ArgumentCaptor<Object> fields = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventWriter).write(anyString(), anyString(), anyString(), fields.capture());
        assertThat(((OrderNotificationFields) fields.getValue()).reason()).isEqualTo("Cancelled by seller");
    }

    @Test
    void oldOrderWithoutEmail_isSkippedSilently() {
        publisher().publish(orderWithEmail(null), OrderStage.DELIVERED, null);
        publisher().publish(orderWithEmail("  "), OrderStage.DELIVERED, null);

        verifyNoInteractions(outboxEventWriter);
    }

    @Test
    @DisplayName("never throws: a notification problem must not undo an order status change")
    void aProblemWritingTheOutboxNeverBreaksTheStatusChange() {
        doThrow(new IllegalStateException("db down")).when(outboxEventWriter).write(anyString(), anyString(), anyString(), any());

        assertThatCode(() -> publisher().publish(orderWithEmail("asha@example.com"), OrderStage.DELIVERED, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("inside a transaction nothing is written until the order change has committed")
    void insideTransaction_writesOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher().publish(orderWithEmail("asha@example.com"), OrderStage.DELIVERED, null);

        verifyNoInteractions(outboxEventWriter);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(outboxEventWriter).write(eq("OrderNotificationEvent"), eq("order.notification"), eq("10"), any());
    }

    @Test
    @DisplayName("if the order change rolls back, no notification is written")
    void rolledBack_writesNothing() {
        TransactionSynchronizationManager.initSynchronization();

        publisher().publish(orderWithEmail("asha@example.com"), OrderStage.DELIVERED, null);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(outboxEventWriter);
    }

    @Test
    @DisplayName("a failing write after commit is swallowed: the order change is already safe")
    void failingWriteAfterCommit_doesNotThrow() {
        doThrow(new IllegalStateException("value too long")).when(outboxEventWriter).write(anyString(), anyString(), anyString(), any());
        TransactionSynchronizationManager.initSynchronization();

        publisher().publish(orderWithEmail("asha@example.com"), OrderStage.PACKED, null);

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
    }
}
