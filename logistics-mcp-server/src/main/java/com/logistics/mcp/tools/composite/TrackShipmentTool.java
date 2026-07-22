package com.logistics.mcp.tools.composite;

import com.logistics.mcp.common.SystemFailureRetryExecutor;
import com.logistics.mcp.common.ToolResult;
import com.logistics.mcp.tools.BusinessTool;
import com.logistics.mcp.tools.domain.Shipment;
import com.logistics.mcp.tools.domain.ShipmentDomainTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * 业务编排工具：查件（运单追踪）。
 *
 * <p>无需用户身份验证，直接查询运单状态与路由。
 * 失败即终止：运单不存在立即返回业务失败。
 */
@Service
public class TrackShipmentTool implements BusinessTool {

    private final ShipmentDomainTools shipmentDomainTools;
    private final SystemFailureRetryExecutor retryExecutor;

    public TrackShipmentTool(ShipmentDomainTools shipmentDomainTools) {
        this.shipmentDomainTools = shipmentDomainTools;
        this.retryExecutor = new SystemFailureRetryExecutor();
    }

    @Tool(description = "查件：查询运单当前状态与路由，无需身份验证，直接按运单 ID 查询。")
    public ToolResult<Shipment> trackShipment(
            @ToolParam(description = "运单 ID") long shipmentId) {

        ToolResult<Shipment> shipmentResult = retryExecutor.execute("get_shipment",
                () -> shipmentDomainTools.getShipment(shipmentId));
        if (!shipmentResult.isSuccess()) {
            return shipmentResult.propagateFailure("查询运单信息失败: ");
        }

        return shipmentResult;
    }
}
