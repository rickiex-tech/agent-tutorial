package com.logistics.agent.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpClientAuthHeaderConfig} 注入的 {@link WebClient.Builder} Filter
 * 能正确把 {@link PermissionContextHolder} 的上下文透传为 HTTP Header。
 *
 * <p>该测试不依赖 Spring 容器，直接构造 Filter 并验证请求 Header，可在 CI 中稳定运行。
 */
class McpClientAuthHeaderConfigTest {

    private McpClientAuthHeaderConfig config;

    @BeforeEach
    void setUp() {
        config = new McpClientAuthHeaderConfig();
        PermissionContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        PermissionContextHolder.clear();
    }

    @Test
    @DisplayName("存在权限上下文时：注入 userId / roles / sessionId 三个 Header")
    void injectsHeadersWhenContextPresent() {
        // given：设置权限上下文
        PermissionContextHolder.set(new PermissionContext("sess-1001", 1001L, "agent-user"));

        // when：构建 WebClient 并发起请求（通过捕获 ExchangeFunction 拦截请求）
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient client = config.mcpWebClientBuilder()
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.error(new RuntimeException("test capture"));
                })
                .build();

        client.get().uri("http://localhost/test").retrieve()
                .toBodilessEntity()
                .onErrorResume(e -> Mono.empty())
                .block();

        // then：验证 Header
        ClientRequest request = captured.get();
        assertThat(request.headers().getFirst("X-User-Id")).isEqualTo("1001");
        assertThat(request.headers().getFirst("X-User-Roles")).isEqualTo("agent-user");
        assertThat(request.headers().getFirst("X-Session-Id")).isEqualTo("sess-1001");
    }

    @Test
    @DisplayName("无权限上下文时：注入 anonymous 作为 X-User-Id，避免 MCP Server 401")
    void injectsAnonymousWhenContextAbsent() {
        // given：不设置权限上下文（握手阶段场景）
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient client = config.mcpWebClientBuilder()
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.error(new RuntimeException("test capture"));
                })
                .build();

        client.get().uri("http://localhost/test").retrieve()
                .toBodilessEntity()
                .onErrorResume(e -> Mono.empty())
                .block();

        // then：注入 anonymous
        ClientRequest request = captured.get();
        assertThat(request.headers().getFirst("X-User-Id")).isEqualTo("anonymous");
        assertThat(request.headers().getFirst("X-User-Roles")).isNull();
        assertThat(request.headers().getFirst("X-Session-Id")).isNull();
    }

    @Test
    @DisplayName("roles 为空时不注入 X-User-Roles Header")
    void omitsRolesHeaderWhenBlank() {
        // given：roles 为空
        PermissionContextHolder.set(new PermissionContext("sess-1001", 1001L, ""));
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient client = config.mcpWebClientBuilder()
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.error(new RuntimeException("test capture"));
                })
                .build();

        client.get().uri("http://localhost/test").retrieve()
                .toBodilessEntity()
                .onErrorResume(e -> Mono.empty())
                .block();

        // then
        ClientRequest request = captured.get();
        assertThat(request.headers().getFirst("X-User-Id")).isEqualTo("1001");
        assertThat(request.headers().getFirst("X-User-Roles")).isNull();
    }

    @Test
    @DisplayName("sessionId 为空时不注入 X-Session-Id Header")
    void omitsSessionIdHeaderWhenBlank() {
        // given：sessionId 为空
        PermissionContextHolder.set(new PermissionContext("", 1001L, "agent-user"));
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient client = config.mcpWebClientBuilder()
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.error(new RuntimeException("test capture"));
                })
                .build();

        client.get().uri("http://localhost/test").retrieve()
                .toBodilessEntity()
                .onErrorResume(e -> Mono.empty())
                .block();

        // then
        ClientRequest request = captured.get();
        assertThat(request.headers().getFirst("X-User-Id")).isEqualTo("1001");
        assertThat(request.headers().getFirst("X-User-Roles")).isEqualTo("agent-user");
        assertThat(request.headers().getFirst("X-Session-Id")).isNull();
    }
}

