package com.supplychain.controltower.ai.agents;

import com.supplychain.controltower.ai.tools.InventoryTools;
import com.supplychain.controltower.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryAgentTest {

    @Mock
    private InventoryTools inventoryTools;

    @Mock
    private ChatClient chatClient;

    private InventoryAgent inventoryAgent;

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId(114L);
        inventoryAgent = new InventoryAgent(inventoryTools, chatClient);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("1. Timeout configuration defaults to 15s and accepts custom value")
    void testTimeoutConfiguration() {
        assertEquals(15L, inventoryAgent.getTimeoutSeconds(), "Default timeout must be 15 seconds");
        inventoryAgent.setTimeoutSeconds(30L);
        assertEquals(30L, inventoryAgent.getTimeoutSeconds(), "Timeout should be configurable");
    }

    @Test
    @DisplayName("2. Fallback for arithmetic query returns clear AI-unavailable message instead of generic overstock report")
    void testFallbackForArithmeticQueryReturnsAiUnavailable() {
        String prompt = "Calculate 150 divided by 20.";

        String result = inventoryAgent.generateFallbackAnalysis(prompt);

        assertNotNull(result);
        assertTrue(result.contains("AI Reasoning Offline"), "Should state AI Reasoning is Offline");
        assertTrue(result.contains("Calculate 150 divided by 20."), "Should echo the query");
        assertFalse(result.contains("Overstocked Items"), "Must never return generic overstock report for arithmetic query");
        assertFalse(result.contains("Stockout Risk Items"), "Must not return stockout report for arithmetic query");

        // Verify inventory tools were never invoked
        verifyNoInteractions(inventoryTools);
    }

    @Test
    @DisplayName("3. Fallback for math expression returns AI-unavailable message")
    void testFallbackForMathExpressionReturnsAiUnavailable() {
        String prompt = "What is 150 / 20?";

        String result = inventoryAgent.generateFallbackAnalysis(prompt);

        assertNotNull(result);
        assertTrue(result.contains("AI Reasoning Offline"));
        assertFalse(result.contains("Overstocked Items"));
        verifyNoInteractions(inventoryTools);
    }

    @Test
    @DisplayName("4. Fallback for general unsupported query returns AI-unavailable message")
    void testFallbackForGeneralQueryReturnsAiUnavailable() {
        String prompt = "What is the capital of France?";

        String result = inventoryAgent.generateFallbackAnalysis(prompt);

        assertNotNull(result);
        assertTrue(result.contains("AI Reasoning Offline"));
        assertFalse(result.contains("Overstocked Items"));
        verifyNoInteractions(inventoryTools);
    }

    @Test
    @DisplayName("5. Fallback for relevant inventory-analysis query preserves database-backed analysis")
    void testFallbackForRelevantInventoryAnalysisQueryPreservesDatabaseAnalysis() {
        String prompt = "Which products are at risk of stockout?";

        InventoryTools.InventoryItemRecord lowStockItem = new InventoryTools.InventoryItemRecord(
                26L, "SKU-TEST-001", "Wireless Mouse", "Raipur Central Warehouse", 5, 20, 10
        );
        InventoryTools.InventoryItemRecord overstockItem = new InventoryTools.InventoryItemRecord(
                27L, "SKU-TEST-002", "Mechanical Keyboard", "Bhilai Distribution Hub", 180, 20, 10
        );

        when(inventoryTools.getLowStockProducts()).thenReturn(List.of(lowStockItem));
        when(inventoryTools.getOverstockedProducts()).thenReturn(List.of(overstockItem));

        String result = inventoryAgent.generateFallbackAnalysis(prompt);

        assertNotNull(result);
        assertTrue(result.contains("Inventory Control Agent Analysis"));
        assertTrue(result.contains("SKU-TEST-001"));
        assertTrue(result.contains("Wireless Mouse"));
        assertTrue(result.contains("SKU-TEST-002"));
        assertTrue(result.contains("Mechanical Keyboard"));
        assertFalse(result.contains("AI Reasoning Offline"));

        verify(inventoryTools, times(1)).getLowStockProducts();
        verify(inventoryTools, times(1)).getOverstockedProducts();
    }

    @Test
    @DisplayName("6. Fallback for clean inventory returns normal buffer message")
    void testFallbackForCleanInventoryReturnsNormalBufferMessage() {
        String prompt = "Check inventory status across warehouses";

        when(inventoryTools.getLowStockProducts()).thenReturn(List.of());
        when(inventoryTools.getOverstockedProducts()).thenReturn(List.of());

        String result = inventoryAgent.generateFallbackAnalysis(prompt);

        assertNotNull(result);
        assertTrue(result.contains("No stockout risks or extreme overstock conditions detected"));
        assertFalse(result.contains("AI Reasoning Offline"));

        verify(inventoryTools, times(1)).getLowStockProducts();
        verify(inventoryTools, times(1)).getOverstockedProducts();
    }

    @Test
    @DisplayName("7. isInventoryAnalysisQuery correctly classifies arithmetic vs inventory queries")
    void testIsInventoryAnalysisQueryClassification() {
        // Arithmetic & general queries -> false
        assertFalse(inventoryAgent.isInventoryAnalysisQuery("Calculate 150 divided by 20."));
        assertFalse(inventoryAgent.isInventoryAnalysisQuery("calculate 100 * 5"));
        assertFalse(inventoryAgent.isInventoryAnalysisQuery("What is 150 / 20?"));
        assertFalse(inventoryAgent.isInventoryAnalysisQuery("Divide 500 by 10"));
        assertFalse(inventoryAgent.isInventoryAnalysisQuery("What is the capital of France?"));
        assertFalse(inventoryAgent.isInventoryAnalysisQuery(null));
        assertFalse(inventoryAgent.isInventoryAnalysisQuery("   "));

        // Domain inventory queries -> true
        assertTrue(inventoryAgent.isInventoryAnalysisQuery("Which products are at risk of stockout?"));
        assertTrue(inventoryAgent.isInventoryAnalysisQuery("Show overstocked inventory across warehouses"));
        assertTrue(inventoryAgent.isInventoryAnalysisQuery("Check current SKU reorder levels"));
        assertTrue(inventoryAgent.isInventoryAnalysisQuery("What is the stock level of SKU-TEST-001?"));
        assertTrue(inventoryAgent.isInventoryAnalysisQuery("Are there any inventory shortages?"));
    }

    @Test
    @DisplayName("8. processQuery with unconfigured API key routes arithmetic to AI-unavailable and inventory to DB fallback")
    void testProcessQueryWithUnconfiguredApiKey() {
        // 1. Arithmetic query
        String calcResult = inventoryAgent.processQuery("Calculate 150 divided by 20.");
        assertTrue(calcResult.contains("AI Reasoning Offline"));
        assertFalse(calcResult.contains("Overstocked Items"));

        // 2. Inventory query
        when(inventoryTools.getLowStockProducts()).thenReturn(List.of());
        when(inventoryTools.getOverstockedProducts()).thenReturn(List.of());
        String invResult = inventoryAgent.processQuery("Check stockout risks");
        assertTrue(invResult.contains("No stockout risks or extreme overstock conditions detected")
                || invResult.contains("Inventory Control Agent Analysis"));
    }

    @Test
    @DisplayName("9. processQuery preserves caller TenantContext when fallback executes")
    void testProcessQueryPreservesCallerTenantContextWhenFallbackExecutes() {
        assertEquals(114L, TenantContext.getCompanyId(), "Precondition: TenantContext must be 114L");

        // Execute arithmetic fallback
        inventoryAgent.processQuery("Calculate 150 divided by 20.");
        assertEquals(114L, TenantContext.getCompanyId(), "Caller TenantContext must be preserved after arithmetic fallback");

        // Execute inventory fallback
        when(inventoryTools.getLowStockProducts()).thenReturn(List.of());
        when(inventoryTools.getOverstockedProducts()).thenReturn(List.of());
        inventoryAgent.processQuery("Check stockout risks");
        assertEquals(114L, TenantContext.getCompanyId(), "Caller TenantContext must be preserved after inventory fallback");
    }

    @Test
    @DisplayName("10. Retry configuration defaults to 3 attempts with 1000ms backoff and accepts custom values")
    void testRetryConfigurationDefaultsAndSetters() {
        assertEquals(3, inventoryAgent.getMaxAttempts(), "Default maxAttempts must be 3");
        assertEquals(1000L, inventoryAgent.getRetryBackoffMs(), "Default retryBackoffMs must be 1000ms");

        inventoryAgent.setMaxAttempts(5);
        inventoryAgent.setRetryBackoffMs(500L);

        assertEquals(5, inventoryAgent.getMaxAttempts());
        assertEquals(500L, inventoryAgent.getRetryBackoffMs());
    }

    @Test
    @DisplayName("11. isTransientLlmError correctly classifies transient vs non-transient failures")
    void testTransientLlmErrorClassification() {
        // Transient HTTP status codes
        assertTrue(inventoryAgent.isTransientLlmError(
                new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "503 UNAVAILABLE: This model is currently experiencing high demand")));
        assertTrue(inventoryAgent.isTransientLlmError(
                new HttpServerErrorException(HttpStatus.BAD_GATEWAY, "502 Bad Gateway")));
        assertTrue(inventoryAgent.isTransientLlmError(
                new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT, "504 Gateway Timeout")));
        assertTrue(inventoryAgent.isTransientLlmError(
                new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "429 Too Many Requests")));

        // Transient network/IO errors
        assertTrue(inventoryAgent.isTransientLlmError(new ResourceAccessException("I/O error on POST request")));
        assertTrue(inventoryAgent.isTransientLlmError(new SocketTimeoutException("Read timed out")));
        assertTrue(inventoryAgent.isTransientLlmError(new ConnectException("Connection refused")));

        // Nested transient cause
        assertTrue(inventoryAgent.isTransientLlmError(
                new RuntimeException("Outer wrapper", new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable"))));

        // Keywords in message
        assertTrue(inventoryAgent.isTransientLlmError(new RuntimeException("Error: upstream high demand spike")));
        assertTrue(inventoryAgent.isTransientLlmError(new RuntimeException("Service temporarily unavailable")));

        // Non-transient errors (must not retry)
        assertFalse(inventoryAgent.isTransientLlmError(
                new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Invalid prompt schema")));
        assertFalse(inventoryAgent.isTransientLlmError(
                new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Invalid API key")));
        assertFalse(inventoryAgent.isTransientLlmError(new IllegalArgumentException("Null argument")));
        assertFalse(inventoryAgent.isTransientLlmError(new NullPointerException("NPE")));
        assertFalse(inventoryAgent.isTransientLlmError(null));
    }

    @Test
    @DisplayName("12. executePromptWithRetry succeeds on second attempt after transient 503 error")
    void testExecutePromptWithRetrySucceedsOnSecondAttempt() {
        ChatClient deepChatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        InventoryAgent agentWithMock = new InventoryAgent(inventoryTools, deepChatClient);
        agentWithMock.setRetryBackoffMs(5L); // 5ms backoff for fast testing

        when(deepChatClient.prompt()
                .system(InventoryAgent.INVENTORY_AGENT_SYSTEM_PROMPT)
                .user("Calculate 150 divided by 20.")
                .functions("getLowStockProducts", "getOverstockedProducts")
                .call()
                .content())
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "This model is currently experiencing high demand"))
                .thenReturn("150 divided by 20 is 7.5");

        String result = agentWithMock.executePromptWithRetry("Calculate 150 divided by 20.");

        assertEquals("150 divided by 20 is 7.5", result);
        verify(deepChatClient.prompt()
                .system(InventoryAgent.INVENTORY_AGENT_SYSTEM_PROMPT)
                .user("Calculate 150 divided by 20.")
                .functions("getLowStockProducts", "getOverstockedProducts")
                .call(), times(2)).content();
    }

    @Test
    @DisplayName("13. executePromptWithRetry fails immediately without retrying on non-transient error")
    void testExecutePromptWithRetryFailsImmediatelyOnNonTransientError() {
        ChatClient deepChatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        InventoryAgent agentWithMock = new InventoryAgent(inventoryTools, deepChatClient);
        agentWithMock.setRetryBackoffMs(5L);

        when(deepChatClient.prompt()
                .system(InventoryAgent.INVENTORY_AGENT_SYSTEM_PROMPT)
                .user("Malformed prompt")
                .functions("getLowStockProducts", "getOverstockedProducts")
                .call()
                .content())
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request: invalid token"));

        assertThrows(HttpClientErrorException.class, () ->
                agentWithMock.executePromptWithRetry("Malformed prompt"));

        verify(deepChatClient.prompt()
                .system(InventoryAgent.INVENTORY_AGENT_SYSTEM_PROMPT)
                .user("Malformed prompt")
                .functions("getLowStockProducts", "getOverstockedProducts")
                .call(), times(1)).content();
    }

    @Test
    @DisplayName("14. executePromptWithRetry exhausts 3 retry attempts on persistent 503 and rethrows exception")
    void testExecutePromptWithRetryExhaustsRetries() {
        ChatClient deepChatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        InventoryAgent agentWithMock = new InventoryAgent(inventoryTools, deepChatClient);
        agentWithMock.setRetryBackoffMs(5L);
        agentWithMock.setMaxAttempts(3);

        when(deepChatClient.prompt()
                .system(InventoryAgent.INVENTORY_AGENT_SYSTEM_PROMPT)
                .user("Calculate 150 divided by 20.")
                .functions("getLowStockProducts", "getOverstockedProducts")
                .call()
                .content())
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "503 UNAVAILABLE: high demand"));

        assertThrows(HttpServerErrorException.class, () ->
                agentWithMock.executePromptWithRetry("Calculate 150 divided by 20."));

        verify(deepChatClient.prompt()
                .system(InventoryAgent.INVENTORY_AGENT_SYSTEM_PROMPT)
                .user("Calculate 150 divided by 20.")
                .functions("getLowStockProducts", "getOverstockedProducts")
                .call(), times(3)).content();
    }

    @Test
    @DisplayName("15. processQuery retries transient 503, succeeds on attempt 2, and preserves caller TenantContext")
    void testProcessQueryRetriesTransient503AndSucceeds() {
        ChatClient deepChatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        InventoryAgent agentWithMock = new InventoryAgent(inventoryTools, deepChatClient);
        agentWithMock.setApiKeyOverride("test-gemini-key");
        agentWithMock.setRetryBackoffMs(5L);

        when(deepChatClient.prompt()
                .system(InventoryAgent.INVENTORY_AGENT_SYSTEM_PROMPT)
                .user("Calculate 150 divided by 20.")
                .functions("getLowStockProducts", "getOverstockedProducts")
                .call()
                .content())
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "503 UNAVAILABLE: high demand"))
                .thenReturn("150 divided by 20 is 7.5");

        String result = agentWithMock.processQuery("Calculate 150 divided by 20.");

        assertEquals("150 divided by 20 is 7.5", result);
        assertEquals(114L, TenantContext.getCompanyId(), "Caller TenantContext must remain preserved after successful query");
    }

    @Test
    @DisplayName("16. processQuery exhausts retries on persistent 503, executes safe fallback, and preserves caller TenantContext")
    void testProcessQueryExhaustsRetriesAndFallsBackWithTenantContext() {
        ChatClient deepChatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        InventoryAgent agentWithMock = new InventoryAgent(inventoryTools, deepChatClient);
        agentWithMock.setApiKeyOverride("test-gemini-key");
        agentWithMock.setRetryBackoffMs(5L);
        agentWithMock.setMaxAttempts(3);

        when(deepChatClient.prompt()
                .system(InventoryAgent.INVENTORY_AGENT_SYSTEM_PROMPT)
                .user("Calculate 150 divided by 20.")
                .functions("getLowStockProducts", "getOverstockedProducts")
                .call()
                .content())
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "503 UNAVAILABLE: high demand"));

        String result = agentWithMock.processQuery("Calculate 150 divided by 20.");

        assertNotNull(result);
        assertTrue(result.contains("AI Reasoning Offline"), "Exhausted retries must safely fall back");
        assertTrue(result.contains("Calculate 150 divided by 20."));
        assertEquals(114L, TenantContext.getCompanyId(), "Caller TenantContext must remain preserved after fallback");
    }
}
