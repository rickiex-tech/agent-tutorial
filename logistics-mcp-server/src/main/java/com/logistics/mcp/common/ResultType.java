package com.logistics.mcp.common;

/**
 * 工具调用结果分类（对应 design.md 决策 4：失败语义）。
 *
 * <ul>
 *   <li>SUCCESS：调用成功。</li>
 *   <li>BUSINESS_FAILURE：业务规则失败（如用户不存在、运单不存在）。终止，不重试。</li>
 *   <li>SYSTEM_FAILURE：系统原因失败（如超时、上游异常）。可重试。</li>
 * </ul>
 */
public enum ResultType {
    SUCCESS("success"),
    BUSINESS_FAILURE("business_failure"),
    SYSTEM_FAILURE("system_failure");

    private final String code;

    ResultType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
