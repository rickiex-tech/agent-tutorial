package com.logistics.agent.session;

import java.util.Optional;

public interface AgentSessionMapper {

    AgentSessionEntity upsert(String sessionId, long userId, String status, String context);

    Optional<AgentSessionEntity> findBySessionId(String sessionId);
}
