package com.logistics.mcp.tools.data;

/**
 * 数据工具示例：运营指标。
 */
public record OrderMetrics(long totalOrders, long delayedOrders, double delayRate) {
}
