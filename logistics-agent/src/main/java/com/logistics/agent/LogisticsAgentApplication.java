package com.logistics.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 物流智能体消费侧启动类。
 *
 * <p>通过 Spring AI 的 MCP Client 连接 {@code logistics-mcp-server}（Streamable HTTP，8080），
 * 将远程 MCP 工具注册为 LLM 可调用的 {@code ToolCallback}，由通义千问自主编排。
 */
@SpringBootApplication
public class LogisticsAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisticsAgentApplication.class, args);
    }
}
