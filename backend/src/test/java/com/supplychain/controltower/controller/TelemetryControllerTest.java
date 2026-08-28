package com.supplychain.controltower.controller;

import com.supplychain.controltower.entity.TelemetryEventEntity;
import com.supplychain.controltower.repository.TelemetryEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelemetryControllerTest {

    private TelemetryEventRepository telemetryEventRepository;
    private TelemetryController telemetryController;

    @BeforeEach
    void setUp() {
        telemetryEventRepository = mock(TelemetryEventRepository.class);
        telemetryController = new TelemetryController(telemetryEventRepository);
    }

    @Test
    void testGetRecentEvents_Success() {
        TelemetryEventEntity event = TelemetryEventEntity.builder()
                .id(1L)
                .eventId("test-event-1")
                .eventType("STOCKOUT_ALERT")
                .severity("WARNING")
                .sourceDomain("INVENTORY")
                .message("Low stock threshold breached")
                .createdAt(LocalDateTime.now())
                .build();

        when(telemetryEventRepository.findByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(List.of(event));

        ResponseEntity<List<TelemetryEventEntity>> response = telemetryController.getRecentEvents(10);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("test-event-1", response.getBody().get(0).getEventId());
        verify(telemetryEventRepository, times(1)).findByOrderByCreatedAtDesc(any(Pageable.class));
    }

    @Test
    void testGetActiveAlerts_Success() {
        TelemetryEventEntity alert = TelemetryEventEntity.builder()
                .id(2L)
                .eventId("alert-event-2")
                .eventType("SHIPMENT_DELAY")
                .severity("ERROR")
                .sourceDomain("LOGISTICS")
                .message("Carrier delay detected")
                .createdAt(LocalDateTime.now())
                .build();

        when(telemetryEventRepository.findActiveAlerts(any(Pageable.class))).thenReturn(List.of(alert));

        ResponseEntity<List<TelemetryEventEntity>> response = telemetryController.getActiveAlerts(10);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("alert-event-2", response.getBody().get(0).getEventId());
        verify(telemetryEventRepository, times(1)).findActiveAlerts(any(Pageable.class));
    }
}
