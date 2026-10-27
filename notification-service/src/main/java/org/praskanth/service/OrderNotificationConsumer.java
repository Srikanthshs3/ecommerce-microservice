package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.events.OrderNotificationEvent;
import org.praskanth.mail.OrderEmailTemplates;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Listens to "order.notification" (published by order-service at every customer-facing step of an order) and sends the
 * matching e-mail. If sending fails the event is NOT acknowledged: the error handler retries it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationConsumer {

    private final EmailService emailService;

    /** Web app address (APP_FRONTEND_URL): the "View my orders" button links to <this>/orders. */
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @KafkaListener(topics = "order.notification",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void onOrderNotification(OrderNotificationEvent event, Acknowledgment ack) {
        if (event.getCustomerEmail() == null || event.getCustomerEmail().toString().isBlank()) {
            log.warn("Order {} {} notification has no e-mail address, skipping", event.getOrderId(), event.getStage());
            ack.acknowledge();
            return;
        }
        String trackUrl = frontendUrl.replaceAll("/+$", "") + "/orders";
        OrderEmailTemplates.Mail mail = OrderEmailTemplates.build(event, trackUrl);
        if (mail == null) {
            log.warn("Unknown order stage '{}' for order {}, skipping", event.getStage(), event.getOrderId());
            ack.acknowledge();
            return;
        }
        emailService.sendHtmlEmail(event.getCustomerEmail().toString(), mail.subject(), mail.html(), mail.text());
        ack.acknowledge();
    }
}
