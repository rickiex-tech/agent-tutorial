package com.logistics.mcp.tools.data;

import com.logistics.mcp.common.ToolResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * 数据域 MCP 工具骨架。
 */
@Service
public class DataDomainTools {

    @Tool(description = "查询运营指标（数据域工具层示例）")
    public ToolResult<OrderMetrics> queryOrderMetrics(
            @ToolParam(description = "统计日期，格式 yyyy-MM-dd") String bizDate) {
        if (bizDate == null || bizDate.isBlank()) {
            return ToolResult.businessFailure(40010, "bizDate is required");
        }
        // PoC: mock 指标结果，真实环境改为 Data API 调用。
        OrderMetrics metrics = new OrderMetrics(1024L, 82L, 0.0801D);
        return ToolResult.success(metrics);
    }
}
