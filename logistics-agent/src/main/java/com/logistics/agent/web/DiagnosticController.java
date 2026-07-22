package com.logistics.agent.web;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 临时诊断端点：列出 ToolCallbackProvider 中实际注册的工具名。
 * 排查 "No ToolCallback found for tool name" 错误时使用。
 */
@RestController
@RequestMapping("/api/v1/diag")
public class DiagnosticController {

    private final ToolCallbackProvider toolCallbackProvider;

    public DiagnosticController(ToolCallbackProvider toolCallbackProvider) {
        this.toolCallbackProvider = toolCallbackProvider;
    }

    @GetMapping("/tools")
    public Map<String, Object> listRegisteredTools() {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        List<Map<String, String>> tools = Arrays.stream(callbacks)
                .map(c -> Map.of(
                        "name", c.getToolDefinition().name(),
                        "description", c.getToolDefinition().description() == null ? "" : c.getToolDefinition().description()
                ))
                .toList();
        return Map.of(
                "count", callbacks.length,
                "providerClass", toolCallbackProvider.getClass().getName(),
                "tools", tools
        );
    }
}
