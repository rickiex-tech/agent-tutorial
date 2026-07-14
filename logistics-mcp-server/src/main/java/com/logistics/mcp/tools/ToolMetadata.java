package com.logistics.mcp.tools;

/**
 * 工具元数据：工具名、分组、描述。
 */
public record ToolMetadata(
        String name,
        String layer,  // composite / domain / data
        String description
) {
}
