package com.logistics.agent.web;

import com.logistics.agent.service.AgentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能体对话入口。
 *
 * <p>{@code POST /api/v1/agent/chat}，接收用户自然语言诉求，返回智能体答复。
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String reply = agentService.chat(request.message());
        return new ChatResponse(reply);
    }

    public record ChatRequest(String message) {
    }

    public record ChatResponse(String reply) {
    }
}
