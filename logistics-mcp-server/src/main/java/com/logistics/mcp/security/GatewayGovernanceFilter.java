package com.logistics.mcp.security;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地治理层模拟：统一认证、限流、上下文传递、基础可观测性。
 */
@Component
public class GatewayGovernanceFilter extends OncePerRequestFilter {

    private static final DateTimeFormatter WINDOW_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
            .withZone(ZoneOffset.UTC);

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicInteger> windowCounter = new ConcurrentHashMap<>();

    @Value("${logistics.mcp.governance.require-auth-header:true}")
    private boolean requireAuthHeader;

    @Value("${logistics.mcp.governance.auth-header-name:X-User-Id}")
    private String authHeaderName;

    @Value("${logistics.mcp.governance.rate-limit-per-minute:120}")
    private int rateLimitPerMinute;

    @Value("${logistics.mcp.governance.excluded-auth-paths:}")
    private String excludedAuthPathsStr;

    private List<String> excludedAuthPaths;

    @jakarta.annotation.PostConstruct
    public void init() {
        if (excludedAuthPathsStr != null && !excludedAuthPathsStr.isBlank()) {
            excludedAuthPaths = Arrays.asList(excludedAuthPathsStr.split(","));
        } else {
            excludedAuthPaths = List.of();
        }
    }

    public GatewayGovernanceFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        
        // 检查是否为豁免鉴权路径（如 MCP 握手）
        boolean isExcludedPath = excludedAuthPaths != null && excludedAuthPaths.stream()
                .anyMatch(requestUri::startsWith);
        
        String userId = request.getHeader(authHeaderName);
        if (!isExcludedPath && requireAuthHeader && (userId == null || userId.isBlank())) {
            meterRegistry.counter("logistics.gateway.auth.reject.count").increment();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("missing auth header: " + authHeaderName);
            return;
        }

        String window = WINDOW_FORMATTER.format(Instant.now());
        String key = (userId == null ? "anonymous" : userId) + ":" + window;
        int current = windowCounter.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        if (current > rateLimitPerMinute) {
            meterRegistry.counter("logistics.gateway.ratelimit.reject.count").increment();
            response.setStatus(429);
            response.getWriter().write("rate limit exceeded");
            return;
        }

        PermissionContextHolder.set(new PermissionContext(
                userId == null ? "anonymous" : userId,
                request.getHeader("X-User-Roles"),
                request.getHeader("X-Session-Id")));
        meterRegistry.counter("logistics.gateway.accept.count").increment();
        try {
            filterChain.doFilter(request, response);
        } finally {
            PermissionContextHolder.clear();
        }
    }
}
