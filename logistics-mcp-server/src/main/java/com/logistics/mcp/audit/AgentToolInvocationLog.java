package com.logistics.mcp.audit;

import java.time.LocalDateTime;

public record AgentToolInvocationLog(
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
