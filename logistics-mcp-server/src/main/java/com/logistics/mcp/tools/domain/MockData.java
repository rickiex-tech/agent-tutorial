package com.logistics.mcp.tools.domain;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock 数据，模拟既有微服务中的数据。
 * 真实环境中由领域工具经 API Gateway 调用既有微服务获取。
 */
public final class MockData {

    private MockData() {
    }

    /** 模拟用户服务超时（系统失败）的用户 ID。 */
    public static final long UNAVAILABLE_USER_ID = 1500L;

    /** 模拟客服工单服务不可用（系统失败）的用户 ID。 */
    public static final long TICKET_SERVICE_DOWN_USER_ID = 1002L;

    public static final Map<Long, User> USERS = Map.of(
            1001L, new User(1001L, "张三", "138****0001", "VIP"),
            1002L, new User(1002L, "李四", "139****0002", "NORMAL"),
            1003L, new User(1003L, "王五", "137****0003", "NORMAL")
    );

    public static final Map<Long, Shipment> SHIPMENTS = Map.of(
            9001L, new Shipment(9001L, 1001L, ShipmentStatus.IN_TRANSIT,       "北京→上海"),
            9002L, new Shipment(9002L, 1003L, ShipmentStatus.DELIVERED,        "广州→深圳"),
            9003L, new Shipment(9003L, 1001L, ShipmentStatus.PENDING_PICKUP,   "上海→杭州"),
            9004L, new Shipment(9004L, 1001L, ShipmentStatus.RETURNED,         "北京→天津"),
            9005L, new Shipment(9005L, 1003L, ShipmentStatus.OUT_FOR_DELIVERY, "深圳→广州"),
            9006L, new Shipment(9006L, 1003L, ShipmentStatus.EXCEPTION,        "成都→重庆")
    );

    public static final AtomicLong TICKET_SEQ = new AtomicLong(70000L);
}
