package com.logistics.mcp.common;

/**
 * 失败语义公共判定逻辑。
 */
public final class FailureSemantics {

    private FailureSemantics() {
    }

    public static boolean isBusinessFailure(ToolResult<?> result) {
        return result.resultType() == ResultType.BUSINESS_FAILURE;
    }

    public static boolean isSystemFailure(ToolResult<?> result) {
        return result.resultType() == ResultType.SYSTEM_FAILURE;
    }

    public static boolean shouldRetry(ToolResult<?> result) {
        return isSystemFailure(result);
    }
}
