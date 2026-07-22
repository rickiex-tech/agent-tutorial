package com.logistics.mcp.tools.domain;

/** 运单状态枚举（对应既有运单域微服务返回的状态值）。 */
public enum ShipmentStatus {
    /** 待取件 */
    PENDING_PICKUP,
    /** 运输中 */
    IN_TRANSIT,
    /** 派送中 */
    OUT_FOR_DELIVERY,
    /** 已签收 */
    DELIVERED,
    /** 已退回 */
    RETURNED,
    /** 异常件 */
    EXCEPTION
}
