package com.logistics.mcp.tools.domain;

/** 运单信息（对应既有运单域微服务返回）。 */
public record Shipment(long shipmentId, long userId, String status, String route) {
}
