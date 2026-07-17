package com.logistics.mcp.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;

/**
 * 工具注册表服务：从已注册的 ToolCallbackProvider 中读取工具元数据。
 */
@Service
public class ToolRegistryService {

    private final ToolCallbackProvider toolCallbackProvider;

    public ToolRegistryService(ToolCallbackProvider toolCallbackProvider) {
        this.toolCallbackProvider = toolCallbackProvider;
    }

    /**
     * 获取所有已注册工具的元数据列表。
     * @return 工具元数据列表，按工具名称推断分组（composite / domain / data）
     */
    public List<ToolMetadata> listAllTools() {
        return Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(this::toMetadata)
                .toList();
    }

    /**
     * 按 layer 分组获取工具。
     * @param layer composite / domain / data
     * @return 指定分组内的工具列表
     */
    public List<ToolMetadata> listToolsByLayer(String layer) {
        return listAllTools().stream()
                .filter(t -> t.layer().equals(layer))
                .toList();
    }

    private ToolMetadata toMetadata(ToolCallback callback) {
        String name = callback.getToolDefinition().name();
        String description = callback.getToolDefinition().description();
        return new ToolMetadata(name, inferLayer(name), description);
    }

    /**
     * 从工具名称推断工具分组。
     * @param toolName 工具方法名
     * @return composite / domain / data
     */
    private String inferLayer(String toolName) {
        String lower = toolName.toLowerCase();
        if (lower.contains("customerservice") || lower.contains("composite")) {
            return "composite";
        } else if (lower.contains("metric") || lower.contains("data")) {
            return "data";
        } else {
            return "domain";
        }
    }
}
