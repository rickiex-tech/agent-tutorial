package com.logistics.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

/**
 * 智能体服务：基于 Spring AI {@link ChatClient}，将通义千问与 MCP 远程工具拼装在一起。
 *
 * <p>核心设计（对应 design.md 决策 8）：
 * <ul>
 *   <li>MCP Client 提供的 {@link ToolCallbackProvider} 把远程工具注册给 LLM，由其自主决定调用时机与参数；</li>
 *   <li>失败语义纯 prompt 驱动 —— 工具返回的 {@code ToolResult.resultType} 原样回灌给 LLM，
 *       由 system prompt 规定三类结果的用户可见行为，agent 侧不写任何重试代码。</li>
 * </ul>
 */
@Service
public class AgentService {

    private static final String SYSTEM_PROMPT = """
            你是物流公司的客服智能助手。你可以调用工具来查询用户、运单信息并创建客服工单。

            工具调用规则：
            - 当用户的诉求涉及"为某用户的某运单创建工单/投诉/报修"时，优先调用 createCustomerServiceTicket 这一业务编排工具，
              它会串行完成 查询用户 → 查询运单 → 创建工单，无需你自己拼装底层工具。
            - 调用工具后会得到一个包含 resultType 字段的结果，你必须依据 resultType 决定如何答复：

              · SUCCESS：操作成功。用自然语言把结果告诉用户（例如告知生成的工单号）。
              · BUSINESS_FAILURE：这是业务规则导致的失败（如运单不存在、用户不存在）。
                把失败原因（message）翻译成简洁的人话解释给用户，引导其核对信息。
                禁止重试、禁止编造任何不存在的数据。
              · SYSTEM_FAILURE：这是系统临时故障（如上游服务不可用）。
                明确告诉用户"系统繁忙，请稍后重试"，不要反复调用同一工具。

            回复要简洁、专业、用中文。不要暴露内部字段名（如 resultType、code），用自然语言表达即可。
            """;

    private final ChatClient chatClient;

    public AgentService(ChatClient.Builder chatClientBuilder, ToolCallbackProvider mcpToolCallbackProvider) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(mcpToolCallbackProvider)
                .build();
    }

    /**
     * 处理一条用户消息，返回智能体的自然语言答复。
     *
     * @param message 用户的自然语言诉求
     * @return 智能体答复
     */
    public String chat(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}