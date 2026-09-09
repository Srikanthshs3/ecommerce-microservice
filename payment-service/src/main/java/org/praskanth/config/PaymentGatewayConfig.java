package org.praskanth.config;

import org.praskanth.entity.PaymentGateway;
import org.praskanth.gateway.PaymentGatewayStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class PaymentGatewayConfig {

    @Bean
    public Map<PaymentGateway, PaymentGatewayStrategy> gatewayStrategies(List<PaymentGatewayStrategy> strategies) {
        return strategies.stream()
                .collect(Collectors.toMap(PaymentGatewayStrategy::getGateway, Function.identity()));
    }
}
