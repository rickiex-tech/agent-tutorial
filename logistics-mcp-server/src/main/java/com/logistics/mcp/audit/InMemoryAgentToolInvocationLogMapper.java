package com.logistics.mcp.audit;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryAgentToolInvocationLogMapper implements AgentToolInvocationLogMapper {

    private final AtomicLong idGenerator = new AtomicLong(0L);
    private final CopyOnWriteArrayList<AgentToolInvocationLog> logs = new CopyOnWriteArrayList<>();

    @Override
    public AgentToolInvocationLog insert(AgentToolInvocationLog log) {
        AgentToolInvocationLog saved = new AgentToolInvocationLog(
                idGenerator.incrementAndGet(),
                log.sessionId(),
                log.toolName(),
                log.toolLayer(),
                log.requestParams(),
                log.responseSummary(),
                log.resultType(),
                log.durationMs(),
                LocalDateTime.now(),
                LocalDateTime.now());
        logs.add(saved);
        return saved;
    }

    @Override
    public List<AgentToolInvocationLog> findAll() {
        return List.copyOf(logs);
    }
}
