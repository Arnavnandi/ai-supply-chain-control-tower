package com.supplychain.controltower.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychain.controltower.dto.TelemetryEvent;
import com.supplychain.controltower.websocket.TelemetryWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryEventPublisherTest {

    @Mock
    private TelemetryWebSocketHandler webSocketHandler;

    @Mock
    private com.supplychain.controltower.repository.TelemetryEventRepository telemetryEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TelemetryEventPublisher telemetryEventPublisher;

    private TelemetryEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = TelemetryEvent.builder()
                .eventType(TelemetryEvent.EventType.STOCKOUT_ALERT)
                .severity(TelemetryEvent.Severity.WARNING)
                .sourceDomain("INVENTORY")
                .entityId("SKU-ELEC-001")
                .message("Low stock threshold breached for SKU-ELEC-001")
                .metadata(Map.of("availableQty", 5, "reorderLevel", 20))
                .build();
    }

    @Test
    void publish_ShouldSerializeAndBroadcastMessage_WhenEventProvided() {
        telemetryEventPublisher.publish(testEvent);
        verify(webSocketHandler, times(1)).broadcastMessage(anyString());
        verify(telemetryEventRepository, times(1)).save(any());
    }

    @Test
    void publish_ShouldHandleNullEventGracefully() {
        telemetryEventPublisher.publish(null);
        verify(webSocketHandler, never()).broadcastMessage(anyString());
    }

    @Test
    void getConnectedClientCount_ShouldDelegateToWebSocketHandler() {
        when(webSocketHandler.getActiveSessionCount()).thenReturn(3);
        assertEquals(3, telemetryEventPublisher.getConnectedClientCount());
    }
}
