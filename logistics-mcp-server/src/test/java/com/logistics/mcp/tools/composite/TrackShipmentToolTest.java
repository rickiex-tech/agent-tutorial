package com.logistics.mcp.tools.composite;

import com.logistics.mcp.common.ResultType;
import com.logistics.mcp.common.ToolResult;
import com.logistics.mcp.tools.domain.Shipment;
import com.logistics.mcp.tools.domain.ShipmentDomainTools;
import com.logistics.mcp.tools.domain.ShipmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 查件业务编排工具测试。
 * 覆盖正常路径与运单不存在两个场景（对应 specs/track-shipment）。
 */
class TrackShipmentToolTest {

    private final TrackShipmentTool tool = new TrackShipmentTool(new ShipmentDomainTools());

    @Test
    @DisplayName("正常路径：运单存在，成功返回运单状态与路由")
    void successPath() {
        ToolResult<Shipment> result = tool.trackShipment(9001L);

        assertTrue(result.isSuccess());
        assertEquals(ResultType.SUCCESS, result.resultType());
        assertNotNull(result.data());
        assertEquals(9001L, result.data().shipmentId());
        assertEquals(ShipmentStatus.IN_TRANSIT, result.data().status());
        assertEquals("北京→上海", result.data().route());
    }

    @Test
    @DisplayName("异常路径：运单不存在则返回业务失败")
    void shipmentNotFound() {
        ToolResult<Shipment> result = tool.trackShipment(9999L);

        assertEquals(ResultType.BUSINESS_FAILURE, result.resultType());
        assertNull(result.data());
        assertTrue(result.message().startsWith("查询运单信息失败"));
        assertEquals(false, result.isRetryable());
    }
}
