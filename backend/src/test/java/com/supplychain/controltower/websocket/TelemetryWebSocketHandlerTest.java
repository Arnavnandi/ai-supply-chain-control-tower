package com.supplychain.controltower.websocket;

import com.supplychain.controltower.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryWebSocketHandlerTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private WebSocketSession sessionA;

    @Mock
    private WebSocketSession sessionB;

    private TelemetryWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TelemetryWebSocketHandler(tokenProvider);
    }

    @Test
    void testHandshakeExtractsCompanyIdFromJwtToken() {
        when(tokenProvider.validateToken("token123")).thenReturn(true);
        when(tokenProvider.getCompanyIdFromJwt("token123")).thenReturn(10L);

        Map<String, Object> attributes = new HashMap<>();
        when(sessionA.getUri()).thenReturn(URI.create("/ws/telemetry?token=token123"));
        when(sessionA.getAttributes()).thenReturn(attributes);

        handler.afterConnectionEstablished(sessionA);

        org.junit.jupiter.api.Assertions.assertEquals(10L, attributes.get("companyId"));
    }

    @Test
    void testBroadcastMessageToCompanyDeliversToTargetCompanyAndSkipsOtherCompany() throws Exception {
        Map<String, Object> attrsA = new HashMap<>(Map.of("companyId", 1L));
        Map<String, Object> attrsB = new HashMap<>(Map.of("companyId", 2L));

        when(sessionA.isOpen()).thenReturn(true);
        when(sessionA.getAttributes()).thenReturn(attrsA);

        when(sessionB.isOpen()).thenReturn(true);
        when(sessionB.getAttributes()).thenReturn(attrsB);

        handler.afterConnectionEstablished(sessionA);
        handler.afterConnectionEstablished(sessionB);

        // Broadcast event for Company 1
        handler.broadcastMessageToCompany(1L, "{\"eventType\":\"STOCKOUT\"}");

        verify(sessionA, times(1)).sendMessage(any(TextMessage.class));
        verify(sessionB, never()).sendMessage(any(TextMessage.class));
    }
}
