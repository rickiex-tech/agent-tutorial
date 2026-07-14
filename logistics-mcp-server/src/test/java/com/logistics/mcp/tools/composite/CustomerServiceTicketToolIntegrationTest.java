package com.logistics.mcp.tools.composite;

import com.logistics.mcp.common.ResultType;
import com.logistics.mcp.common.ToolResult;
import com.logistics.mcp.tools.domain.Ticket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编排工具端到端集成验证（基于 Spring 容器）。
 */
@SpringBootTest
class CustomerServiceTicketToolIntegrationTest {

    @Autowired
    private CustomerServiceTicketTool tool;

    @Test
    @DisplayName("场景1：正常路径")
    void successScenario() {
        ToolResult<Ticket> result = tool.createCustomerServiceTicket(1001L, 9001L, "包裹破损");
        assertEquals(ResultType.SUCCESS, result.resultType());
        assertNotNull(result.data());
    }

    @Test
    @DisplayName("场景2：运单不存在 -> 业务失败")
    void shipmentNotFoundScenario() {
        ToolResult<Ticket> result = tool.createCustomerServiceTicket(1001L, 9999L, "投诉");
        assertEquals(ResultType.BUSINESS_FAILURE, result.resultType());
        assertTrue(result.message().contains("查询运单信息失败"));
    }

    @Test
    @DisplayName("场景3：用户服务不可用 -> 系统失败")
    void userServiceUnavailableScenario() {
        ToolResult<Ticket> result = tool.createCustomerServiceTicket(1500L, 9001L, "咨询");
        assertEquals(ResultType.SYSTEM_FAILURE, result.resultType());
    }

    @Test
    @DisplayName("场景4：工单服务不可用 -> 系统失败")
    void ticketServiceUnavailableScenario() {
        ToolResult<Ticket> result = tool.createCustomerServiceTicket(1002L, 9002L, "延误");
        assertEquals(ResultType.SYSTEM_FAILURE, result.resultType());
    }
}
