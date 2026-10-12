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
        org.junit.jupiter.api.Assertions.assertEquals(1, handler.getActiveSessionCount());
    }

    @Test
    void testHandshakeRejectsUnauthenticatedConnectionWithoutToken() throws Exception {
        when(sessionA.getUri()).thenReturn(URI.create("/ws/telemetry"));

        handler.afterConnectionEstablished(sessionA);

        org.junit.jupiter.api.Assertions.assertEquals(0, handler.getActiveSessionCount());
        verify(sessionA, times(1)).close(any(org.springframework.web.socket.CloseStatus.class));
    }

    @Test
    void testHandshakeRejectsInvalidToken() throws Exception {
        when(tokenProvider.validateToken("invalidToken")).thenReturn(false);
        when(sessionA.getUri()).thenReturn(URI.create("/ws/telemetry?token=invalidToken"));

        handler.afterConnectionEstablished(sessionA);

        org.junit.jupiter.api.Assertions.assertEquals(0, handler.getActiveSessionCount());
        verify(sessionA, times(1)).close(any(org.springframework.web.socket.CloseStatus.class));
    }

    @Test
    void testBroadcastMessageToCompanyDeliversToTargetCompanyAndSkipsOtherCompany() throws Exception {
        Map<String, Object> attrsA = new HashMap<>();
        Map<String, Object> attrsB = new HashMap<>();

        when(tokenProvider.validateToken("tokenA")).thenReturn(true);
        when(tokenProvider.getCompanyIdFromJwt("tokenA")).thenReturn(1L);
        when(sessionA.getUri()).thenReturn(URI.create("/ws/telemetry?token=tokenA"));
        when(sessionA.getAttributes()).thenReturn(attrsA);

        when(tokenProvider.validateToken("tokenB")).thenReturn(true);
        when(tokenProvider.getCompanyIdFromJwt("tokenB")).thenReturn(2L);
        when(sessionB.getUri()).thenReturn(URI.create("/ws/telemetry?token=tokenB"));
        when(sessionB.getAttributes()).thenReturn(attrsB);

        handler.afterConnectionEstablished(sessionA);
        handler.afterConnectionEstablished(sessionB);

        when(sessionA.isOpen()).thenReturn(true);
        when(sessionB.isOpen()).thenReturn(true);

        // Broadcast event for Company 1
        handler.broadcastMessageToCompany(1L, "{\"eventType\":\"STOCKOUT\"}");

        verify(sessionA, times(1)).sendMessage(any(TextMessage.class));
        verify(sessionB, never()).sendMessage(any(TextMessage.class));
    }
}
