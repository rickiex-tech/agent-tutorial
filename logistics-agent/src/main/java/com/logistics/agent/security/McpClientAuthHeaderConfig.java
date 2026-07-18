package com.logistics.agent.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * MCP Client 鉴权 Header 注入配置。
 *
 * <p>Spring AI MCP Client 的 WebFlux 自动配置会 clone 容器中的 {@link WebClient.Builder}，
 * 因此只需提供一个自定义 Builder Bean，在其中注册 {@link ExchangeFilterFunction}，
 * 即可把 {@link PermissionContextHolder} 中的权限上下文透传为 HTTP Header，
 * 让 MCP Server 的 {@code GatewayGovernanceFilter} 能对 MCP 流量（{@code /mcp}）做鉴权。
 *
 * <p>注入的 Header（与 MCP Server 端 {@code application.yml} 对齐）：
 * <ul>
 *   <li>{@code X-User-Id}：必填，缺失时 MCP Server 返回 401</li>
 *   <li>{@code X-User-Roles}：可选，透传角色信息</li>
 *   <li>{@code X-Session-Id}：可选，透传会话 ID</li>
 * </ul>
 *
 * <p>设计要点：
 * <ul>
 *   <li>使用 {@link ExchangeFilterFunctions} 在请求发出前读取 ThreadLocal，避免阻塞 Reactor 线程</li>
 *   <li>当上下文缺失时注入默认值 {@code anonymous}，保证握手阶段（尚未进入 Controller）也能通过鉴权</li>
 *   <li>仅对 MCP Server 的请求生效，不影响其他 WebClient 用途</li>
 * </ul>
 */
@Configuration
@ConditionalOnClass(WebClient.Builder.class)
public class McpClientAuthHeaderConfig {

    private static final Logger log = LoggerFactory.getLogger(McpClientAuthHeaderConfig.class);

    /**
     * MCP Server 鉴权 Header 名（与 {@code logistics.mcp.governance.auth-header-name} 对齐）。
     */
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String HEADER_SESSION_ID = "X-Session-Id";

    /**
     * 提供带鉴权 Header 注入的 {@link WebClient.Builder}。
     *
     * <p>Spring AI 的 {@code StreamableHttpWebFluxTransportAutoConfiguration} 会通过
     * {@code ObjectProvider<WebClient.Builder>} 获取此 Bean，调用 {@code clone()} 后
     * 设置 {@code baseUrl}，因此这里注册的 Filter 会被保留。
     */
    @Bean
    public WebClient.Builder mcpWebClientBuilder() {
        return WebClient.builder()
                .filter((request, next) -> {
                    ClientRequest authenticated = ClientRequest.from(request)
                            .headers(headers -> {
                                var ctxOpt = PermissionContextHolder.get();
                                if (ctxOpt.isPresent()) {
                                    var ctx = ctxOpt.get();
                                    headers.set(HEADER_USER_ID, String.valueOf(ctx.userId()));
                                    if (ctx.roles() != null && !ctx.roles().isBlank()) {
                                        headers.set(HEADER_USER_ROLES, ctx.roles());
                                    }
                                    if (ctx.sessionId() != null && !ctx.sessionId().isBlank()) {
                                        headers.set(HEADER_SESSION_ID, ctx.sessionId());
                                    }
                                    if (log.isDebugEnabled()) {
                                        log.debug("MCP 请求注入鉴权 Header: userId={}, roles={}, sessionId={}",
                                                ctx.userId(), ctx.roles(), ctx.sessionId());
                                    }
                                } else {
                                    // 握手阶段或无上下文场景：注入 anonymous，避免 MCP Server 401
                                    headers.set(HEADER_USER_ID, "anonymous");
                                    if (log.isDebugEnabled()) {
                                        log.debug("MCP 请求无权限上下文，注入 anonymous Header");
                                    }
                                }
                            })
                            .build();
                    return next.exchange(authenticated);
                });
    }
}
