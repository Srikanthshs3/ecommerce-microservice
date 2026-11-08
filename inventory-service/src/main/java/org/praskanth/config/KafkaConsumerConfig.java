package org.praskanth.config;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.praskanth.events.OrderCreatedEvent;
import org.praskanth.events.OrderCancelledEvent;
import org.praskanth.events.ShipmentStatusChangedEvent;
import org.praskanth.events.PaymentFailedEvent;
import org.praskanth.events.ProductCreatedEvent;
import org.praskanth.events.ShipmentCreatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * inventory-service consumes four different event types (each a different Avro record), so
 * unlike a service with a single consumer, this needs one ConsumerFactory/container-factory
 * pair PER TYPE - Spring Kafka's generics mean a ConcurrentKafkaListenerContainerFactory is
 * bound to exactly one value type. Each @KafkaListener references its matching bean by name.
 *
 * Every factory below wires in the shared `kafkaErrorHandler` bean (from common-library's
 * KafkaErrorHandlingConfig): retry with backoff, then dead-letter to "<topic>.DLT" instead of
 * retrying forever or dropping the message.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    private Map<String, Object> baseConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        config.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        config.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return config;
    }

    // ---------- ProductCreatedEvent (provisions zero-stock rows) ----------

    @Bean
    public ConsumerFactory<String, ProductCreatedEvent> productCreatedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConfig());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProductCreatedEvent> productCreatedContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ProductCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(productCreatedConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    // ---------- OrderCreatedEvent (triggers reservation) ----------

    @Bean
    public ConsumerFactory<String, OrderCreatedEvent> orderCreatedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConfig());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> orderCreatedContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderCreatedConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    // ---------- PaymentFailedEvent (compensating release) ----------

    @Bean
    public ConsumerFactory<String, PaymentFailedEvent> paymentFailedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConfig());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent> paymentFailedContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentFailedConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    // ---------- ShipmentCreatedEvent (confirms/finalizes the reservation) ----------

    @Bean
    public ConsumerFactory<String, ShipmentCreatedEvent> shipmentCreatedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConfig());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ShipmentCreatedEvent> shipmentCreatedContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ShipmentCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(shipmentCreatedConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    // ---------- OrderCancelledEvent (compensating release: payment window expired, customer/seller cancelled) ----------

    @Bean
    public ConsumerFactory<String, OrderCancelledEvent> orderCancelledConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConfig());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCancelledEvent> orderCancelledContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, OrderCancelledEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderCancelledConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    // ---------- ShipmentStatusChangedEvent (the parcel left: finalise the stock) ----------

    @Bean
    public ConsumerFactory<String, ShipmentStatusChangedEvent> shipmentStatusChangedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConfig());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ShipmentStatusChangedEvent> shipmentStatusChangedContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ShipmentStatusChangedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(shipmentStatusChangedConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
