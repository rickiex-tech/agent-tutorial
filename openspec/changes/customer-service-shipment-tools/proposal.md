## Why

客服场景中，客户最高频的两类诉求——"我的快递到哪了？"（查件）和"我的快递太慢了，帮我催一下"（催件）——目前只能通过创建通用工单来处理，缺乏专属的编排工具，智能体需要自行拼装底层工具，容易出现遗漏校验或状态误判。

## What Changes

- **新增** `TrackShipmentTool`（查件）：编排工具，串行执行 查询用户 → 查询运单 → 返回运单状态与路由，供智能体直接调用。
- **新增** `ExpediteShipmentTool`（催件）：编排工具，串行执行 查询用户 → 查询运单 → 校验运单状态（仅 `IN_TRANSIT` 可催）→ 创建催件工单，状态不符时立即终止并返回业务失败。
- **新增** `ShipmentDomainTools.listShipmentsByUser(userId)`：支持按用户 ID 查询名下所有运单，供查件/催件场景中用户未提供运单号时使用。
- **新增** 工单类型枚举值 `EXPEDITE`，与现有 `GENERAL` 类型区分。
- **更新** MockData，补充按用户查运单的模拟数据支持。

## Capabilities

### New Capabilities

- `track-shipment`: 查件——验证用户身份后查询运单当前状态与路由，支持按运单 ID 或用户 ID（返回最近运单）查询。
- `expedite-shipment`: 催件——验证用户身份、查询运单、校验状态为 IN_TRANSIT 后创建催件工单；DELIVERED 等终态运单直接返回业务失败。

### Modified Capabilities

- `customer-service-ticket`: 补充工单类型字段 `ticketType`（GENERAL / EXPEDITE），影响 `Ticket` 数据模型与 `TicketDomainTools.createTicket` 签名。

## Impact

- **新增文件**：`TrackShipmentTool.java`、`ExpediteShipmentTool.java`（composite 层）
- **修改文件**：`ShipmentDomainTools.java`（新增 listShipmentsByUser）、`Ticket.java`（新增 ticketType 字段）、`TicketDomainTools.java`（createTicket 增加 ticketType 参数）、`MockData.java`（补充数据）
- **不影响**：现有 `CustomerServiceTicketTool` 的行为（向后兼容，ticketType 默认为 GENERAL）
- **无数据库变更**：当前为 Mock 实现，无 DDL/DML 变更
- **无 API 变更**：MCP 工具通过 Spring AI Tool 注解注册，无 REST 接口变更
