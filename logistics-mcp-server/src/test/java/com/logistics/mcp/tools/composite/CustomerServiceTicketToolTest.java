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
 * 客服工单创建业务编排工具测试。
 * 覆盖正常路径与三类异常路径（对应 specs/customer-service-ticket 与 mcp-failure-semantics）。
 */
class CustomerServiceTicketToolTest {

    private final CustomerServiceTicketTool tool = new CustomerServiceTicketTool(
            new UserDomainTools(), new ShipmentDomainTools(), new TicketDomainTools());

    @Test
    @DisplayName("正常路径：用户与运单均存在，成功创建工单")
    void successPath() {
        ToolResult<Ticket> result = tool.createCustomerServiceTicket(1001L, 9001L, "包裹延迟咨询");

        assertTrue(result.isSuccess());
        assertEquals(ResultType.SUCCESS, result.resultType());
        assertNotNull(result.data());
        assertTrue(result.data().ticketId() > 0);
        assertEquals(1001L, result.data().userId());
        assertEquals(9001L, result.data().shipmentId());
        assertEquals(TicketType.GENERAL, result.data().ticketType());
    }

    @Test
    @DisplayName("异常路径1：用户信息查询失败（系统失败）则终止，不创建工单")
    void userQueryFailsTerminates() {
        ToolResult<Ticket> result = tool.createCustomerServiceTicket(
                1500L /* UNAVAILABLE_USER_ID */, 9001L, "任意内容");

        assertEquals(ResultType.SYSTEM_FAILURE, result.resultType());
        assertNull(result.data());
        assertTrue(result.message().startsWith("查询用户信息失败"));
        assertTrue(result.isRetryable());
    }

    @Test
    @DisplayName("异常路径2：运单不存在（业务失败）则终止，不创建工单")
    void shipmentNotFoundTerminates() {
        ToolResult<Ticket> result = tool.createCustomerServiceTicket(1001L, 9999L /* 不存在 */, "任意内容");

        assertEquals(ResultType.BUSINESS_FAILURE, result.resultType());
        assertNull(result.data());
        assertTrue(result.message().startsWith("查询运单信息失败"));
        assertEquals(false, result.isRetryable());
    }

    @Test
    @DisplayName("异常路径3：创建工单失败（系统失败）则报错")
    void createTicketFails() {
        // 用户 1002 存在、运单 9002 存在，但工单服务对该用户不可用
        ToolResult<Ticket> result = tool.createCustomerServiceTicket(
                1002L /* TICKET_SERVICE_DOWN_USER_ID */, 9002L, "任意内容");

        assertEquals(ResultType.SYSTEM_FAILURE, result.resultType());
        assertNull(result.data());
        assertTrue(result.message().startsWith("创建工单失败"));
    }
}
