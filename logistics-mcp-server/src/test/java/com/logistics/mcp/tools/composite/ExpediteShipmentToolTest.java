package com.logistics.mcp.tools.composite;

import com.logistics.mcp.common.ResultType;
import com.logistics.mcp.common.ToolResult;
import com.logistics.mcp.tools.domain.ShipmentDomainTools;
import com.logistics.mcp.tools.domain.Ticket;
import com.logistics.mcp.tools.domain.TicketDomainTools;
import com.logistics.mcp.tools.domain.TicketType;
import com.logistics.mcp.tools.domain.UserDomainTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 催件业务编排工具测试。
 * 覆盖正常路径与四类异常路径（对应 specs/expedite-shipment）。
 */
class ExpediteShipmentToolTest {

    private final ExpediteShipmentTool tool = new ExpediteShipmentTool(
            new UserDomainTools(), new ShipmentDomainTools(), new TicketDomainTools());

    @Test
    @DisplayName("正常路径：用户存在，运单 IN_TRANSIT，成功创建催件工单")
    void successPath_inTransit() {
        ToolResult<Ticket> result = tool.expediteShipment(1001L, 9001L /* IN_TRANSIT */, "请加急配送");

        assertTrue(result.isSuccess());
        assertEquals(ResultType.SUCCESS, result.resultType());
        assertNotNull(result.data());
        assertTrue(result.data().ticketId() > 0);
        assertEquals(TicketType.EXPEDITE, result.data().ticketType());
        assertEquals(1001L, result.data().userId());
        assertEquals(9001L, result.data().shipmentId());
    }

    @Test
    @DisplayName("异常路径1：用户不存在则终止，不执行后续步骤")
    void userNotFoundTerminates() {
        ToolResult<Ticket> result = tool.expediteShipment(
                1500L /* UNAVAILABLE_USER_ID */, 9001L, "请加急");

        assertEquals(ResultType.SYSTEM_FAILURE, result.resultType());
        assertNull(result.data());
        assertTrue(result.message().startsWith("查询用户信息失败"));
    }

    @Test
    @DisplayName("异常路径2：运单不存在则终止，不执行状态校验与工单创建")
    void shipmentNotFoundTerminates() {
        ToolResult<Ticket> result = tool.expediteShipment(1001L, 9999L /* 不存在 */, "请加急");

        assertEquals(ResultType.BUSINESS_FAILURE, result.resultType());
        assertNull(result.data());
        assertTrue(result.message().startsWith("查询运单信息失败"));
    }

    @Test
    @DisplayName("异常路径3：运单已签收（DELIVERED）则拒绝催件，返回业务失败")
    void deliveredShipmentRejected() {
        // 9002 状态为 DELIVERED
        ToolResult<Ticket> result = tool.expediteShipment(1002L, 9002L, "请加急");

        assertEquals(ResultType.BUSINESS_FAILURE, result.resultType());
        assertNull(result.data());
        assertTrue(result.message().contains("运单状态不支持催件"));
        assertEquals(40001, result.code());
    }

    @Test
    @DisplayName("异常路径4：运单已退回（RETURNED）则拒绝催件，返回业务失败")
    void returnedShipmentRejected() {
        // 9004 状态为 RETURNED
        ToolResult<Ticket> result = tool.expediteShipment(1001L, 9004L, "请加急");

        assertEquals(ResultType.BUSINESS_FAILURE, result.resultType());
        assertNull(result.data());
        assertTrue(result.message().contains("运单状态不支持催件"));
        assertEquals(40001, result.code());
    }
}
