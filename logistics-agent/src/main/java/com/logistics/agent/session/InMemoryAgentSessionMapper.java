package com.logistics.agent.session;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@Primary
@ConditionalOnProperty(prefix = "logistics.persistence.in-memory", name = "enabled", havingValue = "true")
public class InMemoryAgentSessionMapper implements AgentSessionMapper {

    private final AtomicLong idGenerator = new AtomicLong(0L);
    private final ConcurrentHashMap<String, AgentSessionEntity> sessions = new ConcurrentHashMap<>();

    @Override
    public AgentSessionEntity upsert(String sessionId, long userId, String status, String context) {
        AgentSessionEntity existing = sessions.get(sessionId);
        LocalDateTime now = LocalDateTime.now();
        AgentSessionEntity saved;
        if (existing == null) {
            saved = new AgentSessionEntity(idGenerator.incrementAndGet(), sessionId, userId, status, context, now, now);
        } else {
            saved = new AgentSessionEntity(existing.id(), sessionId, userId, status, context, existing.createTime(), now);
        }
        sessions.put(sessionId, saved);
        return saved;
    }

    @Override
    public Optional<AgentSessionEntity> findBySessionId(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }
}
