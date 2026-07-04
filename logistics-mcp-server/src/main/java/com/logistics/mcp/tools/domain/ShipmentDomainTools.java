package com.logistics.mcp.tools.domain;

import com.logistics.mcp.common.ToolResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** 运单域领域工具。 */
@Service
public class ShipmentDomainTools {

    /** 查询运单信息。对接 GET /api/v1/shipments/{shipmentId}。 */
    @Tool(description = "查询运单信息，对接运单域微服务 GET /api/v1/shipments/{shipmentId}")
    public ToolResult<Shipment> getShipment(@ToolParam(description = "运单 ID") long shipmentId) {
        Shipment shipment = MockData.SHIPMENTS.get(shipmentId);
        if (shipment == null) {
            return ToolResult.businessFailure(40402, "shipment not found: " + shipmentId);
        }
        return ToolResult.success(shipment);
    }
}
