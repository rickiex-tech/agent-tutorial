package com.logistics.mcp.common;

import java.util.function.Supplier;

/**
 * 受控重试策略：仅对系统失败重试，业务失败立即终止。
 */
public class SystemFailureRetryExecutor {

    private final int maxAttempts;
    private final SimpleCircuitBreaker circuitBreaker;

    public SystemFailureRetryExecutor() {
        this(2, new SimpleCircuitBreaker());
    }

    public SystemFailureRetryExecutor(int maxAttempts, SimpleCircuitBreaker circuitBreaker) {
        this.maxAttempts = maxAttempts;
        this.circuitBreaker = circuitBreaker;
    }

    public <T> ToolResult<T> execute(String operationName, Supplier<ToolResult<T>> supplier) {
        if (!circuitBreaker.allowRequest(operationName)) {
            return ToolResult.systemFailure(50301, "circuit open: " + operationName);
        }

        ToolResult<T> result = null;
        int attempts = 0;
        while (attempts < maxAttempts) {
            attempts++;
            result = supplier.get();
            if (result.resultType() == ResultType.SYSTEM_FAILURE) {
                circuitBreaker.recordFailure(operationName);
                continue;
            }
            circuitBreaker.recordSuccess(operationName);
            return result;
        }
        return result;
    }
}
