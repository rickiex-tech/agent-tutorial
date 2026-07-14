package com.logistics.agent.session;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话状态管理：将多轮上下文持久化到 agent_session。
 */
@Service
public class AgentSessionService {

    private final AgentSessionMapper sessionMapper;
    private final JsonMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public AgentSessionService(AgentSessionMapper sessionMapper, JsonMapper objectMapper, MeterRegistry meterRegistry) {
        this.sessionMapper = sessionMapper;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "sess-" + System.currentTimeMillis();
        }
        return sessionId;
    }

    public void appendUserTurn(String sessionId, long userId, String message) {
        appendTurn(sessionId, userId, "user", message);
    }

    public void appendAssistantTurn(String sessionId, long userId, String message) {
        appendTurn(sessionId, userId, "assistant", message);
    }

    private void appendTurn(String sessionId, long userId, String role, String content) {
        List<Map<String, String>> turns = loadTurns(sessionId);
        Map<String, String> turn = new LinkedHashMap<>();
        turn.put("role", role);
        turn.put("content", content);
        turns.add(turn);
        String context;
        try {
            context = objectMapper.writeValueAsString(turns);
        } catch (Exception ex) {
            context = "[]";
        }
        sessionMapper.upsert(sessionId, userId, "ACTIVE", context);
        meterRegistry.counter("logistics.agent.session.turn.count", "role", role).increment();
    }

    private List<Map<String, String>> loadTurns(String sessionId) {
        return sessionMapper.findBySessionId(sessionId)
                .map(AgentSessionEntity::context)
                .map(this::safeReadTurns)
                .orElseGet(ArrayList::new);
    }

    private List<Map<String, String>> safeReadTurns(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }
}
