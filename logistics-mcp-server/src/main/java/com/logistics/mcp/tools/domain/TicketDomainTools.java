package com.logistics.mcp.tools.domain;

import com.logistics.mcp.common.ToolResult;
import com.logistics.mcp.tools.BusinessTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** 客服域领域工具。 */
@Service
public class TicketDomainTools implements BusinessTool {

    /** 创建工单。对接 POST /api/v1/tickets。 */
    @Tool(description = "创建客服工单，对接客服域微服务 POST /api/v1/tickets")
    public ToolResult<Ticket> createTicket(@ToolParam(description = "用户 ID") long userId,
                                           @ToolParam(description = "运单 ID") long shipmentId,
                                           @ToolParam(description = "工单内容") String content,
                                           @ToolParam(description = "工单类型，GENERAL 或 EXPEDITE") TicketType ticketType) {
        if (userId == MockData.TICKET_SERVICE_DOWN_USER_ID) {
            return ToolResult.systemFailure(50002, "ticket service unavailable");
        }
        Ticket ticket = new Ticket(
                MockData.TICKET_SEQ.incrementAndGet(),
                userId,
                shipmentId,
                content,
                "OPEN",
                ticketType
        );
        return ToolResult.success(ticket);
    }
}
