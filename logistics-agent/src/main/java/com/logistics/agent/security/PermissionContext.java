package com.logistics.agent.security;

public record PermissionContext(String sessionId, long userId, String roles) {
}
