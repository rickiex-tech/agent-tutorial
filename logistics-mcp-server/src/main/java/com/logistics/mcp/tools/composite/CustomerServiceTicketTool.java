package com.logistics.mcp.tools.composite;

import com.logistics.mcp.common.ToolResult;
import com.logistics.mcp.tools.domain.Shipment;
import com.logistics.mcp.tools.domain.ShipmentDomainTools;
import com.logistics.mcp.tools.domain.Ticket;
import com.logistics.mcp.tools.domain.TicketDomainTools;
import com.logistics.mcp.tools.domain.User;
import com.logistics.mcp.tools.domain.UserDomainTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * 业务编排工具：客服工单创建（对应 design.md 决策 5）。
 *
 * <p>串行编排：查询用户信息 → 查询运单信息 → 创建工单。
 * 强前置校验、失败即终止：任意一步未成功，立即终止后续步骤并返回该步骤的失败结果。
 * 智能体只需调用此单一工具，无需自行拼装三个底层工具。
 */
@Service
public class CustomerServiceTicketTool {

    private final UserDomainTools userDomainTools;
    private final ShipmentDomainTools shipmentDomainTools;
    private final TicketDomainTools ticketDomainTools;

    public CustomerServiceTicketTool(UserDomainTools userDomainTools,
                                     ShipmentDomainTools shipmentDomainTools,
                                     TicketDomainTools ticketDomainTools) {
        this.userDomainTools = userDomainTools;
        this.shipmentDomainTools = shipmentDomainTools;
        this.ticketDomainTools = ticketDomainTools;
    }

    @Tool(description = "创建客服工单：串行执行 查询用户信息 → 查询运单信息 → 创建工单，"
            + "强前置校验、失败即终止。任意一步失败立即返回，不执行后续步骤。")
    public ToolResult<Ticket> createCustomerServiceTicket(
            @ToolParam(description = "用户 ID") long userId,
            @ToolParam(description = "运单 ID") long shipmentId,
            @ToolParam(description = "工单内容") String content) {

        // 步骤 1：查询用户信息，失败即终止
        ToolResult<User> userResult = userDomainTools.getUser(userId);
        if (!userResult.isSuccess()) {
            return userResult.propagateFailure("查询用户信息失败: ");
        }

        // 步骤 2：查询运单信息，失败即终止
        ToolResult<Shipment> shipmentResult = shipmentDomainTools.getShipment(shipmentId);
        if (!shipmentResult.isSuccess()) {
            return shipmentResult.propagateFailure("查询运单信息失败: ");
        }

        // 步骤 3：创建工单，失败则报错
        ToolResult<Ticket> ticketResult = ticketDomainTools.createTicket(userId, shipmentId, content);
        if (!ticketResult.isSuccess()) {
            return ticketResult.propagateFailure("创建工单失败: ");
        }

        return ticketResult;
    }
}
