package com.monty.matchbook.config;

import com.monty.matchbook.engine.MatchingEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MatchingConfig {

    @Bean
    MatchingEngine matchingEngine() {
        return new MatchingEngine();
    }
}
