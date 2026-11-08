package org.praskanth.config;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.praskanth.events.*;
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
 * order-service is the Saga's status "scoreboard" - it consumes every outcome event to drive
 * its own order.status through the lifecycle (see OrderStatus). Five event types, five distinct
 * ConsumerFactory/container-factory pairs, same reasoning as inventory-service's KafkaConsumerConfig.
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

    // ---------- InventoryReservedEvent -> AWAITING_PAYMENT ----------

    @Bean
    public ConsumerFactory<String, InventoryReservedEvent> inventoryReservedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConfig());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryReservedEvent> inventoryReservedContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, InventoryReservedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(inventoryReservedConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    // ---------- InventoryReservationFailedEvent -> CANCELLED ----------

    @Bean
    public ConsumerFactory<String, InventoryReservationFailedEvent> inventoryReservationFailedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConfig());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryReservationFailedEvent> inventoryReservationFailedContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, InventoryReservationFailedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(inventoryReservationFailedConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    // ---------- PaymentCompletedEvent -> CONFIRMED ----------

    @Bean
    public ConsumerFactory<String, PaymentCompletedEvent> paymentCompletedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConfig());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent> paymentCompletedContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentCompletedConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    // ---------- PaymentFailedEvent -> CANCELLED ----------

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

    // ---------- ShipmentCreatedEvent -> SHIPPED ----------

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

    // ---------- ShipmentStatusChangedEvent -> READY_TO_SHIP / OUT_FOR_DELIVERY / DELIVERED / CANCELLED ----------

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
