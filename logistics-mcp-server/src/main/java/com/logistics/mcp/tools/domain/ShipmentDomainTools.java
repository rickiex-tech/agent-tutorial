package com.logistics.mcp.tools.domain;

import com.logistics.mcp.common.ToolResult;
import com.logistics.mcp.tools.BusinessTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/** 运单域领域工具。 */
@Service
public class ShipmentDomainTools implements BusinessTool {

    /** 查询运单信息。对接 GET /api/v1/shipments/{shipmentId}。 */
    @Tool(description = "查询运单信息，对接运单域微服务 GET /api/v1/shipments/{shipmentId}")
    public ToolResult<Shipment> getShipment(@ToolParam(description = "运单 ID") long shipmentId) {
        Shipment shipment = MockData.SHIPMENTS.get(shipmentId);
        if (shipment == null) {
            return ToolResult.businessFailure(40402, "shipment not found: " + shipmentId);
        }
        return ToolResult.success(shipment);
    }

    /** 按用户查询名下所有运单。对接 GET /api/v1/users/{userId}/shipments。 */
    @Tool(description = "按用户 ID 查询该用户名下所有运单，对接运单域微服务 GET /api/v1/users/{userId}/shipments")
    public ToolResult<List<Shipment>> listShipmentsByUser(@ToolParam(description = "用户 ID") long userId) {
        List<Shipment> shipments = MockData.SHIPMENTS.values().stream()
                .filter(s -> s.userId() == userId)
                .toList();
        return ToolResult.success(shipments);
    }
}
