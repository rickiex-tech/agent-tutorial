package com.logistics.agent.orchestration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 工具组目录：动态从 MCP Server 工具注册表读取可用工具，按分层组织（业务编排 / 领域 / 数据）。
 *
 * 避免硬编码：当 MCP Server 新增工具时，Agent 自动感知，无需修改代码。
 */
@Component
public class ToolGroupCatalog {

    private static final Logger logger = Logger.getLogger(ToolGroupCatalog.class.getName());

    private final String mcpServerUrl;
    private final WebClient webClient;
    private volatile String cachedDescription; // 简单缓存

    public ToolGroupCatalog(
            @Value("${spring.ai.mcp.client.transport.url:http://localhost:8080}") String mcpServerUrl,
            WebClient.Builder webClientBuilder) {
        this.mcpServerUrl = mcpServerUrl;
        this.webClient = webClientBuilder.baseUrl(mcpServerUrl).build();
    }

    /**
     * 从 MCP Server 工具注册表动态获取工具目录描述。
     * 若无法连接则返回降级描述。
     */
    public String describe() {
        if (cachedDescription != null) {
            return cachedDescription;
        }

        try {
            // 使用 ParameterizedTypeReference 保留泛型信息，避免 unchecked 警告，
            // 同时让 Jackson 正确反序列化为 Map<String, Object>。
            Map<String, Object> response = webClient.get()
                    .uri("/api/v1/tools/describe")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null && response.containsKey("summary")) {
                cachedDescription = (String) response.get("summary");
                return cachedDescription;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to fetch tool registry from " + mcpServerUrl, e);
        }

        // 降级：返回硬编码的默认描述
        return getDefaultDescription();
    }

    /**
     * 按分层类型获取工具列表。
     * @param layer composite / domain / data
     * @return 工具列表，格式 [{name, layer, description}, ...]
     */
    public List<?> getToolsByLayer(String layer) {
        try {
            // 使用 ParameterizedTypeReference 保留 List<Map<String, Object>> 的泛型信息，
            // 避免 bodyToFlux(Map.class) 的 unchecked 转换警告。
            List<Map<String, Object>> tools = webClient.get()
                    .uri("/api/v1/tools/{layer}", layer)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();
            return tools != null ? tools : Collections.emptyList();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to fetch " + layer + " tools from registry", e);
            return Collections.emptyList();
        }
    }

    /**
     * 降级到硬编码描述（当 MCP Server 不可用时）。
     */
    private String getDefaultDescription() {
        return "业务编排工具: createCustomerServiceTicket\n"
                + "领域工具: getUser, getShipment, createTicket\n"
                + "数据工具: queryOrderMetrics";
    }
}
