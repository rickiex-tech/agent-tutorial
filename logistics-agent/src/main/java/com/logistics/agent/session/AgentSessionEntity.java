package com.logistics.agent.session;

import java.time.LocalDateTime;

public record AgentSessionEntity(
        long id,
        String sessionId,
        long userId,
        String status,
        String context,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
