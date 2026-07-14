package com.logistics.mcp.security;

/**
 * 工具调用链路的权限上下文。
 */
public record PermissionContext(String userId, String roles, String sessionId) {
}
