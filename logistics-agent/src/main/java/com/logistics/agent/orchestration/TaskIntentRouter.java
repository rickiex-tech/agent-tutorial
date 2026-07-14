package com.logistics.agent.orchestration;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 任务理解与工具选择提示：优先引导使用业务编排工具。
 */
@Component
public class TaskIntentRouter {

    public String enrichUserMessage(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "工单", "投诉", "报修", "改址", "拦截", "物流轨迹", "运单")) {
            return "请优先调用 createCustomerServiceTicket 或其它业务编排工具处理，再回复用户。\n用户请求：" + message;
        }
        if (containsAny(normalized, "指标", "统计", "报表", "metrics", "数据")) {
            return "这是查询类请求，优先考虑数据域工具 queryOrderMetrics。\n用户请求：" + message;
        }
        return message;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
