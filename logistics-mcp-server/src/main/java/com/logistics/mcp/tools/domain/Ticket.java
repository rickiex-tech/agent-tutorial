package com.logistics.mcp.tools.domain;

/** 工单信息（对应既有客服域微服务返回）。 */
public record Ticket(long ticketId, long userId, long shipmentId, String content, String status, TicketType ticketType) {
}
