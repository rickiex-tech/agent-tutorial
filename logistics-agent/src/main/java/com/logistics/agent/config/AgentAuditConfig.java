package com.logistics.agent.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import com.logistics.agent.audit.AuditingToolCallbackProvider;
import com.logistics.agent.audit.AgentToolInvocationLogMapper;
import com.logistics.agent.orchestration.ToolGroupCatalog;

/**
 * Agent 审计配置：将原始的 ToolCallbackProvider 包装为审计拦截版本。
 */
@Configuration
public class AgentAuditConfig {

    /**
     * 创建审计包装的 ToolCallbackProvider，作为主要的工具回调提供者。
        * 原始的 MCP ToolCallbackProvider 通过 Spring 自动注入。
     */
    @Bean
    @Primary
    public ToolCallbackProvider auditingToolCallbackProvider(
            ToolCallbackProvider delegate,
            ToolGroupCatalog toolGroupCatalog,
            AgentToolInvocationLogMapper auditLogMapper) {
        return new AuditingToolCallbackProvider(delegate, toolGroupCatalog, auditLogMapper);
    }
}
