package com.supplychain.controltower.ai.agents;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ResilienceCircuitBreakerTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private InventoryAgent inventoryAgent;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("llmService");
        if (circuitBreaker != null) {
            circuitBreaker.reset();
        }
    }

    @Test
    void testCircuitBreakerRegistryConfigured() {
        assertNotNull(circuitBreaker, "llmService circuit breaker must be configured");
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void testInventoryAgentProcessQueryFallbackExecution() {
        String result = inventoryAgent.processQuery("Check stockout risks");
        assertNotNull(result);
        assertTrue(result.contains("Inventory Control Agent Analysis") || result.contains("Stockout Risk Items"));
    }
}
