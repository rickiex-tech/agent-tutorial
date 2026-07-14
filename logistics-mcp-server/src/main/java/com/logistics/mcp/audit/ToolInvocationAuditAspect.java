package com.logistics.mcp.audit;

import com.logistics.mcp.common.ResultType;
import com.logistics.mcp.common.ToolResult;
import com.logistics.mcp.security.PermissionContextHolder;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 工具调用审计切面：记录调用日志、脱敏摘要、结果类型与耗时。
 */
@Aspect
@Component
public class ToolInvocationAuditAspect {

    private final AgentToolInvocationLogMapper logMapper;
    private final MeterRegistry meterRegistry;

    public ToolInvocationAuditAspect(AgentToolInvocationLogMapper logMapper, MeterRegistry meterRegistry) {
        this.logMapper = logMapper;
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object aroundTool(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        String toolName = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
        String toolLayer = resolveToolLayer(joinPoint.getTarget().getClass().getName());
        String requestSummary = summarizeRequest(joinPoint.getArgs());
        String resultType = ResultType.SYSTEM_FAILURE.code();
        String responseSummary = "exception";

        try {
            Object response = joinPoint.proceed();
            if (response instanceof ToolResult<?> toolResult) {
                resultType = toolResult.resultType().code();
                responseSummary = summarizeToolResult(toolResult);
            } else {
                resultType = ResultType.SUCCESS.code();
                responseSummary = summarizeObject(response);
            }
            return response;
        } catch (Throwable throwable) {
            responseSummary = "error=" + throwable.getClass().getSimpleName()
                    + ", message=" + mask(String.valueOf(throwable.getMessage()));
            throw throwable;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String sessionId = PermissionContextHolder.get()
                    .map(ctx -> ctx.sessionId() == null ? "unknown" : ctx.sessionId())
                    .orElse("unknown");
            logMapper.insert(new AgentToolInvocationLog(
                    0L,
                    sessionId,
                    toolName,
                    toolLayer,
                    requestSummary,
                    responseSummary,
                    resultType,
                    durationMs,
                    LocalDateTime.now(),
                    LocalDateTime.now()));
            meterRegistry.counter("logistics.tool.invocation.count",
                    "tool", toolName,
                    "layer", toolLayer,
                    "result_type", resultType).increment();
            meterRegistry.timer("logistics.tool.invocation.duration",
                    "tool", toolName,
                    "layer", toolLayer).record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    private String resolveToolLayer(String className) {
        if (className.contains(".composite.")) {
            return "composite";
        }
        if (className.contains(".data.")) {
            return "data";
        }
        if (className.contains(".domain.")) {
            return "domain";
        }
        return "unknown";
    }

    private String summarizeRequest(Object[] args) {
        return mask(Arrays.toString(args));
    }

    private String summarizeToolResult(ToolResult<?> toolResult) {
        String dataType = toolResult.data() == null ? "null" : toolResult.data().getClass().getSimpleName();
        return "code=" + toolResult.code() + ", message=" + mask(toolResult.message())
                + ", resultType=" + toolResult.resultType().code() + ", dataType=" + dataType;
    }

    private String summarizeObject(Object response) {
        if (response == null) {
            return "null";
        }
        return "dataType=" + response.getClass().getSimpleName();
    }

    private String mask(String text) {
        if (text == null) {
            return null;
        }
        return text
                .replaceAll("1\\d{10}", "1**********")
                .replaceAll("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@", "$1***@")
                .replaceAll("(token|secret|password)=[^,\\]]+", "$1=***");
    }
}
