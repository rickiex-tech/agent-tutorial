package com.logistics.agent.web;

import com.logistics.agent.service.AgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 控制器层测试：验证 {@code POST /api/v1/agent/chat} 的请求/响应契约。
 *
 * <p>{@link AgentService} 被 mock，使用 standalone MockMvc，因此该测试不依赖 LLM、
 * API Key 或运行中的 MCP Server，可在 CI 中稳定运行。真实的 LLM 自主编排端到端验证见 README。
 */
class AgentControllerTest {

    private MockMvc mockMvc;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        agentService = mock(AgentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentController(agentService)).build();
    }

    @Test
    void chatReturnsAgentReply() throws Exception {
        given(agentService.chat(anyString()))
                .willReturn("已为您创建工单 70001，我们会尽快跟进。");

        mockMvc.perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"用户1001运单9001破损，帮忙建个工单\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("已为您创建工单 70001，我们会尽快跟进。"));
    }
}
