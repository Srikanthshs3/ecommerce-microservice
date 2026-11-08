package org.praskanth.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.entity.Order;
import org.praskanth.entity.OrderItem;
import org.praskanth.outbox.OrderNotificationFields;
import org.praskanth.outbox.OutboxEventWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Queues an "order notification" in the outbox AFTER the order change has been committed, in its own small transaction.
 *
 *  - An e-mail is only ever announced for a change that really happened (nothing is queued if the change rolls back).
 *  - A problem queuing the e-mail (bad column, database hiccup) can NEVER undo or block the order change itself: an
 *    exception inside the main transaction would mark it rollback-only even if it were caught, so the write is kept
 *    out of it on purpose. The price: if the service crashes between the two commits, that one e-mail is lost.
 *
 * Once queued, the outbox poller delivers the event to Kafka even when Kafka is briefly down, and
 * notification-service turns it into an e-mail.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationPublisher {

    public static final String TOPIC = "order.notification";
    public static final String EVENT_TYPE = "OrderNotificationEvent";

    private final OutboxEventWriter outboxEventWriter;
    private final PlatformTransactionManager transactionManager;

    public void publish(Order order, OrderStage stage, String reason) {
        try {
            if (order.getCustomerEmail() == null || order.getCustomerEmail().isBlank()) {
                log.debug("Order {} has no customer e-mail (placed before e-mails existed): no {} notification", order.getId(), stage);
                return;
            }
            List<OrderNotificationFields.ItemField> items = order.getItems() == null ? List.of()
                    : order.getItems().stream().map(OrderNotificationPublisher::toItem).toList();

            // Everything is read NOW (items may be lazy-loaded inside the transaction); only the write is deferred.
            OrderNotificationFields fields = new OrderNotificationFields(
                    order.getId(),
                    order.getUserId(),
                    stage.name(),
                    order.getCustomerEmail(),
                    order.getCustomerName(),
                    order.getRecipientName(),
                    order.getShippingAddress(),
                    order.getPaymentMethod(),
                    reason,
                    money(order.getTotalAmount()),
                    items,
                    Instant.now());
            String key = String.valueOf(order.getId());

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        write(key, fields, stage);
                    }
                });
            } else {
                write(key, fields, stage);
            }
        } catch (Exception ex) {
            log.warn("Could not queue the {} notification for order {}: {}", stage, order.getId(), ex.getMessage());
        }
    }

    /** Own transaction, never throws. */
    private void write(String key, OrderNotificationFields fields, OrderStage stage) {
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status -> outboxEventWriter.write(EVENT_TYPE, TOPIC, key, fields));
        } catch (Exception ex) {
            log.warn("Could not queue the {} notification for order {}: {}", stage, fields.orderId(), ex.getMessage());
        }
    }

    private static OrderNotificationFields.ItemField toItem(OrderItem i) {
        return new OrderNotificationFields.ItemField(
                i.getProductName() == null ? "Item" : i.getProductName(),
                i.getVariantLabel(),
                i.getQuantity() == null ? 1 : i.getQuantity(),
                money(i.getUnitPrice()));
    }

    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
