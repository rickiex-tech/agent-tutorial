package com.logistics.agent.service;

import com.logistics.agent.security.PermissionContext;
import com.logistics.agent.security.PermissionContextHolder;
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
            你是物流公司的客服智能助手。你可以调用工具来查询用户、运单信息、创建客服工单和加急处理。

            【工具选择指南】优先使用这三个业务编排工具，无需自行拼装底层工具：

            1️⃣ trackShipment（查件）—— 用户查询运单进度或状态
               场景：当用户问"我的运单在哪里"、"物流进度怎么样"、"能帮我查一下我的包裹吗"等
               用法：只需运单 ID，无需用户身份验证
               返回：运单的当前状态和物流路由信息

            2️⃣ expediteShipment（催件）—— 用户要求加急或投诉慢
               场景：当用户说"这个运单太慢了，能加急吗"、"我要催一下"、"我要加急处理"等
               前置条件：该运单必须处于可加急状态（待取件、运输中、派送中、异常件），已签收或已退回的运单不支持催件
               返回：成功创建催件工单或拒绝原因

            3️⃣ createCustomerServiceTicket（创建工单）—— 用户投诉、反馈或其他诉求
               场景：当用户说"我要投诉"、"我要反馈"、"我要报修"、"我要咨询"等，
                    或用户的诉求与加急无关，但需要记录处理
               用法：需要用户 ID 和运单 ID，会串行验证用户和运单再创建工单
               返回：成功创建客服工单，生成工单号

            【结果处理规则】调用工具后会返回一个包含 resultType 的结果，务必依据类型决定答复：

            · SUCCESS（成功）：操作已完成。用自然语言向用户报告成功，例如告知工单号、运单状态等。
            · BUSINESS_FAILURE（业务失败）：这是业务规则导致的失败（如运单不存在、用户不存在、状态不支持加急等）。
              用简洁的人话解释失败原因，引导用户核对信息，禁止重试和编造数据。
            · SYSTEM_FAILURE（系统故障）：这是系统临时故障（如上游服务不可用）。
              明确告诉用户"系统繁忙，请稍后重试"，不要反复调用同一工具。

            【沟通风格】
            - 回复简洁、专业、用中文
            - 不要暴露内部字段名（resultType、code）或 API 细节，用自然语言表达
            - 遇到用户无法直接处理的诉求（如查看多个运单、具体的物流调度等），可以表示理解但需要人工介入

            【用户身份】用户消息开头的【当前用户上下文】给出了当前登录用户的 userId，
            调用需要 userId 参数的工具（如催件、创建工单）时，必须使用该 userId，
            不要从用户消息文本中提取或编造其他 userId。
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
     * <p>将 {@link PermissionContext} 中的 userId 作为上下文前缀注入到用户消息前，
     * 使 LLM 在调用需要 userId 参数的工具（如催件、创建工单）时能直接使用，
     * 避免从自然语言中提取或编造。
     *
     * @param message 用户的自然语言诉求
     * @return 智能体答复
     */
    public String chat(String message) {
        PermissionContext ctx = PermissionContextHolder.get().orElse(null);
        String userPrompt = (ctx == null)
                ? message
                : "【当前用户上下文】userId=" + ctx.userId() + "\n\n" + message;
        return chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();
    }
}