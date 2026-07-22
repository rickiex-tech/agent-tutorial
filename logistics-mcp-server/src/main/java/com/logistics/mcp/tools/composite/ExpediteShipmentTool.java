package com.logistics.mcp.tools.composite;

import com.logistics.mcp.common.SystemFailureRetryExecutor;
import com.logistics.mcp.common.ToolResult;
import com.logistics.mcp.tools.BusinessTool;
import com.logistics.mcp.tools.domain.Shipment;
import com.logistics.mcp.tools.domain.ShipmentDomainTools;
import com.logistics.mcp.tools.domain.ShipmentStatus;
import com.logistics.mcp.tools.domain.TicketType;
import com.logistics.mcp.tools.domain.Ticket;
import com.logistics.mcp.tools.domain.TicketDomainTools;
import com.logistics.mcp.tools.domain.User;
import com.logistics.mcp.tools.domain.UserDomainTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 业务编排工具：催件。
 *
 * <p>串行编排：查询用户信息 → 查询运单信息 → 校验运单状态（白名单）→ 创建催件工单。
 * 强前置校验、失败即终止：任意一步未成功或状态不在白名单内，立即终止并返回失败结果。
 */
@Service
public class ExpediteShipmentTool implements BusinessTool {

    /** 允许催件的运单状态白名单。 */
    private static final Set<ShipmentStatus> EXPEDITABLE_STATUSES = Set.of(
            ShipmentStatus.PENDING_PICKUP,
            ShipmentStatus.IN_TRANSIT,
            ShipmentStatus.OUT_FOR_DELIVERY,
            ShipmentStatus.EXCEPTION
    );

    private final UserDomainTools userDomainTools;
    private final ShipmentDomainTools shipmentDomainTools;
    private final TicketDomainTools ticketDomainTools;
    private final SystemFailureRetryExecutor retryExecutor;

    public ExpediteShipmentTool(UserDomainTools userDomainTools,
                                ShipmentDomainTools shipmentDomainTools,
                                TicketDomainTools ticketDomainTools) {
        this.userDomainTools = userDomainTools;
        this.shipmentDomainTools = shipmentDomainTools;
        this.ticketDomainTools = ticketDomainTools;
        this.retryExecutor = new SystemFailureRetryExecutor();
    }

    @Tool(description = "催件：验证用户身份后查询运单，校验运单处于可催件状态，创建催件工单。"
            + "允许催件的状态：待取件、运输中、派送中、异常件。已签收或已退回的运单不可催件。")
    public ToolResult<Ticket> expediteShipment(
            @ToolParam(description = "用户 ID") long userId,
            @ToolParam(description = "运单 ID") long shipmentId,
            @ToolParam(description = "催件原因或备注") String content) {

        // 步骤 1：查询用户信息，失败即终止
        ToolResult<User> userResult = retryExecutor.execute("get_user",
                () -> userDomainTools.getUser(userId));
        if (!userResult.isSuccess()) {
            return userResult.propagateFailure("查询用户信息失败: ");
        }

        // 步骤 2：查询运单信息，失败即终止
        ToolResult<Shipment> shipmentResult = retryExecutor.execute("get_shipment",
                () -> shipmentDomainTools.getShipment(shipmentId));
        if (!shipmentResult.isSuccess()) {
            return shipmentResult.propagateFailure("查询运单信息失败: ");
        }

        // 步骤 3：校验运单状态（白名单），不通过返回业务失败
        ShipmentStatus status = shipmentResult.data().status();
        if (!EXPEDITABLE_STATUSES.contains(status)) {
            return ToolResult.businessFailure(40001, "运单状态不支持催件: " + status);
        }

        // 步骤 4：创建催件工单
        ToolResult<Ticket> ticketResult = retryExecutor.execute("create_ticket",
                () -> ticketDomainTools.createTicket(userId, shipmentId, content, TicketType.EXPEDITE));
        if (!ticketResult.isSuccess()) {
            return ticketResult.propagateFailure("创建催件工单失败: ");
        }

        return ticketResult;
    }
}
