package com.logistics.agent.audit;

import java.time.LocalDateTime;

public record AgentToolInvocationLogEntity(
        long id,
        String sessionId,
        String toolName,
        String toolLayer,
        String requestParams,
        String responseSummary,
        String resultType,
        long durationMs,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
