package com.logistics.agent.web;

import com.logistics.agent.security.PermissionContext;
import com.logistics.agent.security.PermissionContextHolder;
import com.logistics.agent.service.AgentService;
import com.logistics.agent.session.AgentSessionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 智能体对话入口。
 *
 * <p>{@code POST /api/v1/agent/chat}，接收用户自然语言诉求，返回智能体答复。
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    // 负责调用大模型与工具链。
    private final AgentService agentService;
    // 负责会话ID规范化与多轮上下文持久化。
    private final AgentSessionService sessionService;

    public AgentController(AgentService agentService, AgentSessionService sessionService) {
        this.agentService = agentService;
        this.sessionService = sessionService;
    }

    @PostMapping("/chat")
    /**
     * 单轮对话处理：绑定会话与用户上下文，记录问答轮次，并返回同一 sessionId 供客户端续聊。
     */
    public ChatResponse chat(@RequestBody ChatRequest request) {
        long userId = request.userId() == null ? 1001L : request.userId();
        String sessionId = sessionService.normalizeSessionId(request.sessionId());
        // 将本次请求的会话与权限信息放入 ThreadLocal，供下游链路读取。
        PermissionContextHolder.set(new PermissionContext(sessionId, userId, request.roles()));
        try {
            // 先落用户输入，再调用智能体生成回复，最后落助手回复，保证上下文顺序一致。
            sessionService.appendUserTurn(sessionId, userId, request.message());
            String reply = agentService.chat(request.message());
            sessionService.appendAssistantTurn(sessionId, userId, reply);
            return new ChatResponse(reply, sessionId);
        } finally {
            // 防止线程复用导致上下文污染。
            PermissionContextHolder.clear();
        }
    }

    public record ChatRequest(String message, String sessionId, Long userId, String roles) {
        /**
         * 便捷构造：用于只传 message 的场景，补齐默认会话与身份信息。
         */
        public ChatRequest(String message) {
            this(message, "sess-" + UUID.randomUUID(), 1001L, "agent-user");
        }
    }

    public record ChatResponse(String reply, String sessionId) {
        /**
         * 兼容仅关注回复文本的调用方，不主动返回 sessionId。
         */
        public ChatResponse(String reply) {
            this(reply, null);
        }
    }
}
