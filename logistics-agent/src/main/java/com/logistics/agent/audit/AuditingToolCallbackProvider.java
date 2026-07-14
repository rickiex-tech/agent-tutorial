package com.logistics.agent.audit;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import com.logistics.agent.orchestration.ToolGroupCatalog;
import com.logistics.agent.security.PermissionContextHolder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 审计拦截的工具回调提供者：包装原始的 MCP ToolCallbackProvider，
 * 在工具执行前后记录审计日志，包括 tool_layer 字段。
 * 通过 {@link com.logistics.agent.config.AgentAuditConfig} 装配，不参与组件扫描。
 */
public class AuditingToolCallbackProvider implements ToolCallbackProvider {

    private static final Logger logger = Logger.getLogger(AuditingToolCallbackProvider.class.getName());

    private final ToolCallbackProvider delegate;
    private final ToolGroupCatalog toolGroupCatalog;
    private final AgentToolInvocationLogMapper auditLogMapper;

    public AuditingToolCallbackProvider(
            ToolCallbackProvider delegate,
            ToolGroupCatalog toolGroupCatalog,
            AgentToolInvocationLogMapper auditLogMapper) {
        this.delegate = delegate;
        this.toolGroupCatalog = toolGroupCatalog;
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        // 包装每个 delegate 的工具回调，添加审计拦截
        ToolCallback[] originalCallbacks = delegate.getToolCallbacks();
        ToolCallback[] wrappedCallbacks = new ToolCallback[originalCallbacks.length];
        
        for (int i = 0; i < originalCallbacks.length; i++) {
            wrappedCallbacks[i] = wrapWithAudit(originalCallbacks[i]);
        }
        
        return wrappedCallbacks;
    }

    /**
     * 包装单个工具回调，在执行时记录审计日志。
     */
    private ToolCallback wrapWithAudit(ToolCallback original) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return original.getToolDefinition();
            }

            @Override
            public String call(String request) {
                long startTime = System.currentTimeMillis();
                String toolName = original.getToolDefinition().name();
                String toolLayer = inferToolLayer(toolName);
                String result = null;
                String resultType = "UNKNOWN";
                String errorMsg = null;

                try {
                    // 执行原始工具
                    result = original.call(request);
                    
                    // 尝试从 result 中推测 resultType
                    if (result != null && result.contains("\"resultType\"")) {
                        if (result.contains("\"resultType\":\"SUCCESS\"")) {
                            resultType = "SUCCESS";
                        } else if (result.contains("\"resultType\":\"BUSINESS_FAILURE\"")) {
                            resultType = "BUSINESS_FAILURE";
                        } else if (result.contains("\"resultType\":\"SYSTEM_FAILURE\"")) {
                            resultType = "SYSTEM_FAILURE";
                        }
                    }

                    return result;
                } catch (Exception e) {
                    resultType = "SYSTEM_FAILURE";
                    errorMsg = e.getMessage();
                    throw e;
                } finally {
                    // 记录审计日志
                    long duration = System.currentTimeMillis() - startTime;
                    recordAuditLog(toolName, toolLayer, request, result, resultType, duration, errorMsg);
                }
            }
        };
    }

    /**
     * 推断工具的分层（从工具名或通过 catalog 查询）。
     */
    private String inferToolLayer(String toolName) {
        try {
            // 尝试通过 catalog 查询所有层，找到匹配的工具
            for (String layer : List.of("composite", "domain", "data")) {
                List<?> tools = toolGroupCatalog.getToolsByLayer(layer);
                for (Object tool : tools) {
                    if (tool instanceof Map<?, ?> toolMap && toolName.equals(toolMap.get("name"))) {
                        return layer;
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to infer tool layer for " + toolName, e);
        }
        // 降级：根据工具名推断
        if (toolName.contains("create") && toolName.contains("ticket")) {
            return "composite";
        } else if (toolName.startsWith("get")) {
            return "domain";
        }
        return "data";
    }

    /**
     * 记录工具调用审计日志。
     */
    private void recordAuditLog(
            String toolName,
            String toolLayer,
            String requestParams,
            String result,
            String resultType,
            long durationMs,
            String errorMsg) {
        try {
            // 从 PermissionContextHolder 获取 sessionId
            String sessionId = null;
            var contextOpt = PermissionContextHolder.get();
            if (contextOpt.isPresent()) {
                sessionId = contextOpt.get().sessionId();
            }
            
            String responseSummary = truncateResponse(result);
            AgentToolInvocationLogEntity entity = new AgentToolInvocationLogEntity(
                    0,  // id 自增
                    sessionId,
                    toolName,
                    toolLayer,
                    requestParams,
                    responseSummary,
                    resultType,
                    durationMs,
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
            auditLogMapper.insert(entity);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to record audit log for tool " + toolName, e);
        }
    }

    /**
     * 截断响应摘要（避免存储过长的字符串）。
     */
    private String truncateResponse(String result) {
        if (result == null) {
            return null;
        }
        int maxLength = 500;
        return result.length() > maxLength ? result.substring(0, maxLength) + "..." : result;
    }
}
