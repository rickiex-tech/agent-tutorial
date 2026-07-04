package com.logistics.mcp.common;

/**
 * 统一工具响应格式（对应 design.md 决策 7）。
 *
 * @param code       业务码，0 表示成功
 * @param message    描述信息
 * @param resultType 结果分类（成功 / 业务失败 / 系统失败）
 * @param data       响应数据
 */
public record ToolResult<T>(int code, String message, ResultType resultType, T data) {

    public boolean isSuccess() {
        return resultType == ResultType.SUCCESS;
    }

    /** 仅系统失败可重试。 */
    public boolean isRetryable() {
        return resultType == ResultType.SYSTEM_FAILURE;
    }

    public static <T> ToolResult<T> success(T data) {
        return new ToolResult<>(0, "success", ResultType.SUCCESS, data);
    }

    public static <T> ToolResult<T> businessFailure(int code, String message) {
        return new ToolResult<>(code, message, ResultType.BUSINESS_FAILURE, null);
    }

    public static <T> ToolResult<T> systemFailure(int code, String message) {
        return new ToolResult<>(code, message, ResultType.SYSTEM_FAILURE, null);
    }

    /** 在保留原失败分类与码的前提下，转换为目标数据类型并附加步骤前缀。 */
    public <R> ToolResult<R> propagateFailure(String stepPrefix) {
        return new ToolResult<>(code, stepPrefix + message, resultType, null);
    }
}
