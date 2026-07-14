package com.logistics.mcp.tools;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具注册表 REST 端点：向 Agent 侧暴露可用工具列表。
 */
@RestController
@RequestMapping("/api/v1/tools")
public class ToolRegistryController {

    private final ToolRegistryService toolRegistryService;

    public ToolRegistryController(ToolRegistryService toolRegistryService) {
        this.toolRegistryService = toolRegistryService;
    }

    /**
     * 获取所有已注册工具的列表。
     */
    @GetMapping
    public List<ToolMetadata> getAllTools() {
        return toolRegistryService.listAllTools();
    }

    /**
     * 获取按 layer 分组的工具。
     * @param layer composite / domain / data
     * @return 指定分组内的工具列表
     */
    @GetMapping("/{layer}")
    public List<ToolMetadata> getToolsByLayer(@PathVariable String layer) {
        return toolRegistryService.listToolsByLayer(layer);
    }

    /**
     * 获取工具目录描述（便于调试）。
     * @return 格式化的工具目录字符串
     */
    @GetMapping("/describe")
    public Map<String, Object> describe() {
        List<ToolMetadata> allTools = toolRegistryService.listAllTools();
        Map<String, List<ToolMetadata>> byLayer = allTools.stream()
                .collect(Collectors.groupingBy(ToolMetadata::layer));
        
        return Map.of(
                "total", allTools.size(),
                "byLayer", byLayer,
                "summary", formatSummary(byLayer)
        );
    }

    private String formatSummary(Map<String, List<ToolMetadata>> byLayer) {
        StringBuilder sb = new StringBuilder();
        byLayer.forEach((layer, tools) -> {
            String toolNames = tools.stream()
                    .map(ToolMetadata::name)
                    .collect(Collectors.joining(", "));
            sb.append(layer).append("工具: ").append(toolNames).append("\n");
        });
        return sb.toString();
    }
}
