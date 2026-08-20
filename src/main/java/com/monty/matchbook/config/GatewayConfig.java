package com.monty.matchbook.config;

import com.monty.matchbook.gateway.api.PriceConverter;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class GatewayConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PriceConverter priceConverter(@Value("${matchbook.tick-size}") BigDecimal tickSize) {
        return new PriceConverter(tickSize);
    }
}
