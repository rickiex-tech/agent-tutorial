## 1. 数据模型与 Domain 层准备

- [x] 1.1 新建 `ShipmentStatus` enum，包含 `PENDING_PICKUP`、`IN_TRANSIT`、`OUT_FOR_DELIVERY`、`DELIVERED`、`RETURNED`、`EXCEPTION` 六个值
- [x] 1.2 修改 `Shipment` record，将 `status` 字段类型由 `String` 改为 `ShipmentStatus`
- [x] 1.3 更新 `MockData` 中所有 `Shipment` 构造，使用 `ShipmentStatus` enum 值；补充多用户多运单数据（覆盖各状态），支持 `listShipmentsByUser` 查询场景
- [x] 1.4 为 `Ticket` record 新增 `ticketType` 字段（String 类型，值为 `GENERAL` / `EXPEDITE`）
- [x] 1.5 修改 `TicketDomainTools.createTicket`，增加 `ticketType` 参数，更新方法签名与 Mock 实现
- [x] 1.6 修改 `CustomerServiceTicketTool`，调用 `createTicket` 时传入 `"GENERAL"`，保持原有行为
- [x] 1.7 在 `ShipmentDomainTools` 新增 `listShipmentsByUser(userId)` 方法，返回 `ToolResult<List<Shipment>>`

## 2. 查件工具实现（TrackShipmentTool）

- [x] 2.1 新建 `composite/TrackShipmentTool.java`，注入 `ShipmentDomainTools`、`SystemFailureRetryExecutor`
- [x] 2.2 实现 `@Tool track_shipment(shipmentId)`：执行 getShipment，失败即终止，成功返回 `Shipment`
- [x] 2.3 为 `TrackShipmentTool` 编写集成测试，覆盖：正常路径、运单不存在两个场景

## 3. 催件工具实现（ExpediteShipmentTool）

- [x] 3.1 新建 `composite/ExpediteShipmentTool.java`，注入 `UserDomainTools`、`ShipmentDomainTools`、`TicketDomainTools`、`SystemFailureRetryExecutor`
- [x] 3.2 实现 `@Tool expedite_shipment(userId, shipmentId, content)`：串行执行 getUser → getShipment → 白名单状态校验 → createTicket(EXPEDITE)
- [x] 3.3 实现运单状态白名单校验：使用 exhaustive switch，`PENDING_PICKUP`/`IN_TRANSIT`/`OUT_FOR_DELIVERY`/`EXCEPTION` 放行，其余返回 `ToolResult.businessFailure(40001, "运单状态不支持催件")`
- [x] 3.4 为 `ExpediteShipmentTool` 编写集成测试，覆盖：正常路径（IN_TRANSIT）、用户不存在、运单不存在、DELIVERED 拒绝、RETURNED 拒绝五个场景

## 4. 智能体工具注册验证

- [x] 4.1 确认 `TrackShipmentTool` 和 `ExpediteShipmentTool` 被 Spring 自动扫描注册为 MCP Tool（检查 `ToolRegistryService`）
- [x] 4.2 验证 `/api/v1/tools` 端点返回新工具的注册信息
- [x] 4.3 更新 `CustomerServiceTicketTool` 相关测试，断言 `Ticket.ticketType` 为 `"GENERAL"`

## 5. 回归验证

- [x] 5.1 运行 `mvn test`，确保所有原有测试仍通过
- [x] 5.2 通过 `requests.http` 手动调用智能体，验证查件/催件对话场景端到端正常
