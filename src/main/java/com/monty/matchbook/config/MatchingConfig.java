package com.monty.matchbook.config;

import com.monty.matchbook.engine.MatchingEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The engine carries no Spring annotations — that is the point of keeping {@code engine/}
 * framework-free — so its bean is declared here instead.
 */
@Configuration
class MatchingConfig {

    @Bean
    MatchingEngine matchingEngine() {
        return new MatchingEngine();
    }
}
