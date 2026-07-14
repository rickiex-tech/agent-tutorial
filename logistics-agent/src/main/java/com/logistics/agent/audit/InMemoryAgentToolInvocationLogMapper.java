package com.logistics.agent.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@Primary
@ConditionalOnProperty(prefix = "logistics.persistence.in-memory", name = "enabled", havingValue = "true")
public class InMemoryAgentToolInvocationLogMapper implements AgentToolInvocationLogMapper {

    private final AtomicLong idGenerator = new AtomicLong(0L);
    private final CopyOnWriteArrayList<AgentToolInvocationLogEntity> logs = new CopyOnWriteArrayList<>();

    @Override
    public AgentToolInvocationLogEntity insert(AgentToolInvocationLogEntity entity) {
        AgentToolInvocationLogEntity saved = new AgentToolInvocationLogEntity(
                idGenerator.incrementAndGet(),
                entity.sessionId(),
                entity.toolName(),
                entity.toolLayer(),
                entity.requestParams(),
                entity.responseSummary(),
                entity.resultType(),
                entity.durationMs(),
                LocalDateTime.now(),
                LocalDateTime.now());
        logs.add(saved);
        return saved;
    }

    @Override
    public List<AgentToolInvocationLogEntity> findAll() {
        return List.copyOf(logs);
    }
}
