# logistics-mcp-server — 物流业务能力 MCP 工具服务

物流业务能力的 MCP 工具服务，验证 `build-internal-agent-mcp-architecture` change 的核心架构：
**按业务域封装领域工具 + 对稳定串行链路做业务编排工具 + 统一失败语义**。

> 范围说明：当前工作区无既有 300+ API 与基础设施，本服务用 **mock 数据**模拟既有微服务，
> 聚焦证明「客服工单创建」这一条链路的架构可行性。完整企业级落地见
> `openspec/changes/build-internal-agent-mcp-architecture/`。

## 技术栈

- Spring Boot 4.0.5 / Spring AI 2.0.0 / Java 21 / Maven
- 领域工具与业务编排工具通过 Spring AI `@Tool` 暴露为 **MCP tools**
  （`spring-ai-starter-mcp-server-webmvc`）

## 架构对应

```
智能体 (MCP client)
  │  只看到少量高层工具，而非底层 300+ API
  ▼
业务编排工具层  CustomerServiceTicketTool.createCustomerServiceTicket
  │  串行：查询用户 → 查询运单 → 创建工单，强前置校验、失败即终止
  ▼
领域工具层  getUser / getShipment / createTicket   （按业务域封装，mock 数据）
  ▼
（真实环境）API Gateway → 既有微服务 200+ 业务 API
```

## 关键设计

- **统一响应** [`ToolResult`](src/main/java/com/logistics/mcp/common/ToolResult.java)：`code / message / resultType / data`
- **失败分类** [`ResultType`](src/main/java/com/logistics/mcp/common/ResultType.java)：
  - `BUSINESS_FAILURE`（用户/运单不存在）→ 终止，不重试
  - `SYSTEM_FAILURE`（超时/上游不可用）→ 可重试（`isRetryable()`）
- **串行编排 + 失败即终止**：任意一步未成功，立即返回该步骤失败结果，不执行后续步骤

## 运行

```bash
export JAVA_HOME=/path/to/jdk-21
cd logistics-mcp-server

# 运行测试（正常路径 + 三类异常路径）
mvn test

# 启动 MCP server（将工具暴露给 MCP client）
mvn spring-boot:run
```

## 测试覆盖

| 场景 | 输入 | 期望结果 |
|------|------|----------|
| 正常路径 | user=1001, shipment=9001 | SUCCESS，返回 ticketId |
| 用户查询失败 | user=1500 | SYSTEM_FAILURE，终止，可重试 |
| 运单不存在 | user=1001, shipment=9999 | BUSINESS_FAILURE，终止，不重试 |
| 创建工单失败 | user=1002, shipment=9002 | SYSTEM_FAILURE，报错 |
