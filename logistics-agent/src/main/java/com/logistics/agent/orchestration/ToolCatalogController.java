package com.logistics.agent.orchestration;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * 工具目录端点：向用户展示当前可用的工具列表。
 */
@RestController
@RequestMapping("/api/v1/tools")
public class ToolCatalogController {

    private final ToolGroupCatalog toolGroupCatalog;

    public ToolCatalogController(ToolGroupCatalog toolGroupCatalog) {
        this.toolGroupCatalog = toolGroupCatalog;
    }

    /**
     * 获取工具目录描述（从 MCP Server 动态加载）。
     */
    @GetMapping("/catalog")
    public Map<String, String> getCatalog() {
        return Map.of(
                "description", toolGroupCatalog.describe(),
                "source", "MCP Server tool registry (dynamic)"
        );
    }

    /**
     * 按分层获取工具列表。
     * @param layer composite / domain / data
     */
    @GetMapping("/{layer}")
    public Map<String, Object> getToolsByLayer(@PathVariable String layer) {
        List<?> tools = toolGroupCatalog.getToolsByLayer(layer);
        return Map.of(
                "layer", layer,
                "count", tools.size(),
                "tools", tools
        );
    }
}
