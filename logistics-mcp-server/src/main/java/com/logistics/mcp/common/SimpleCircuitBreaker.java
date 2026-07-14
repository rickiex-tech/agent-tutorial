package com.logistics.mcp.common;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量级熔断器，用于本仓库 PoC 的治理模拟。
 */
public class SimpleCircuitBreaker {

    private final int failureThreshold;
    private final long openMillis;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public SimpleCircuitBreaker() {
        this(5, Duration.ofSeconds(20));
    }

    public SimpleCircuitBreaker(int failureThreshold, Duration openDuration) {
        this.failureThreshold = failureThreshold;
        this.openMillis = openDuration.toMillis();
    }

    public boolean allowRequest(String operationName) {
        State state = states.get(operationName);
        if (state == null) {
            return true;
        }
        if (state.openUntilEpochMs > System.currentTimeMillis()) {
            return false;
        }
        if (state.openUntilEpochMs > 0) {
            state.openUntilEpochMs = 0;
            state.failureCount = 0;
        }
        return true;
    }

    public void recordSuccess(String operationName) {
        states.computeIfAbsent(operationName, key -> new State()).reset();
    }

    public void recordFailure(String operationName) {
        State state = states.computeIfAbsent(operationName, key -> new State());
        state.failureCount++;
        if (state.failureCount >= failureThreshold) {
            state.openUntilEpochMs = System.currentTimeMillis() + openMillis;
        }
    }

    private static class State {
        private int failureCount;
        private long openUntilEpochMs;

        private void reset() {
            this.failureCount = 0;
            this.openUntilEpochMs = 0;
        }
    }
}
