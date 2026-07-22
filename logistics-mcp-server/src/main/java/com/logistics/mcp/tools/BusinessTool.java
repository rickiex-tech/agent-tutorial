package com.logistics.mcp.tools;

/**
 * 标记接口：标识业务编排与领域工具（受 {@code logistics.mcp.tools.business-enabled} 开关控制）。
 *
 * <p>实现此接口的 Spring Bean 会被自动扫描注册为 MCP 工具，
 * 无需在 {@code LogisticsMcpServerApplication} 的 {@code @Bean} 方法中手动声明。
 *
 * <p>新增业务工具时，只需让类实现此接口并标注 {@code @Service} + {@code @Tool} 即可。
 */
public interface BusinessTool {
}
