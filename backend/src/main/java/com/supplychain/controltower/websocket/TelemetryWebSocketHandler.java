package com.supplychain.controltower.websocket;

import com.supplychain.controltower.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final JwtTokenProvider tokenProvider;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long companyId = extractCompanyIdFromSession(session);
        if (companyId != null) {
            session.getAttributes().put("companyId", companyId);
        }
        sessions.add(session);
        log.info("[WEBSOCKET TELEMETRY] Client connected: {} | CompanyId: {} | Total sessions: {}", session.getId(), companyId, sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("[WEBSOCKET TELEMETRY] Client disconnected: {} | Total sessions: {}", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[WEBSOCKET TELEMETRY] Session transport error: {} | Message: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
        try {
            session.close();
        } catch (IOException ignored) {}
    }

    public void broadcastMessage(String jsonMessage) {
        broadcastMessageToCompany(null, jsonMessage);
    }

    public void broadcastMessageToCompany(Long targetCompanyId, String jsonMessage) {
        if (sessions.isEmpty()) {
            return;
        }

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }

            Long sessionCompanyId = (Long) session.getAttributes().get("companyId");
            if (targetCompanyId != null && sessionCompanyId != null && !targetCompanyId.equals(sessionCompanyId)) {
                continue; // Isolate telemetry to target company
            }

            try {
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (Exception ex) {
                log.warn("[WEBSOCKET TELEMETRY FAIL] Failed to send event to session {}: {}", session.getId(), ex.getMessage());
                sessions.remove(session);
            }
        }
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    private Long extractCompanyIdFromSession(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri != null && uri.getQuery() != null) {
                for (String param : uri.getQuery().split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2 && "token".equalsIgnoreCase(kv[0])) {
                        String token = kv[1];
                        if (StringUtils.hasText(token) && tokenProvider != null && tokenProvider.validateToken(token)) {
                            return tokenProvider.getCompanyIdFromJwt(token);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[WEBSOCKET AUTH FAIL] Could not extract token from websocket handshake: {}", ex.getMessage());
        }
        return null;
    }
}
