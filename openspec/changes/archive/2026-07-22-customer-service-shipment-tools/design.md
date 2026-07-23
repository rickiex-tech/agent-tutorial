## Context

当前 MCP 服务已有一个业务编排工具 `CustomerServiceTicketTool`，实现"查用户 → 查运单 → 创建工单"的串行编排模式，并以此作为参考实现。客服场景中最高频的两类诉求——**查件**（查询运单当前状态/路由）和**催件**（针对在途运单发起加急请求）——目前需要智能体自行拼装底层工具，存在遗漏状态校验的风险。

本设计在同一 `composite/` 层新增两个编排工具，复用已有 domain 工具，保持架构一致性。

## Goals / Non-Goals

**Goals:**
- 新增 `TrackShipmentTool`，封装查件编排逻辑，智能体无需感知底层步骤
- 新增 `ExpediteShipmentTool`，封装催件编排逻辑，含运单状态前置校验
- 在 `ShipmentDomainTools` 补充 `listShipmentsByUser`，支持按用户查所有运单
- 为 `Ticket` 引入 `ticketType` 字段（`GENERAL` / `EXPEDITE`），与创建工单工具向后兼容

**Non-Goals:**
- 不引入真实的运单/用户微服务调用（继续使用 Mock 数据）
- 不修改 HTTP 接口或 REST API
- 不新增数据库表（当前为 Mock 实现）
- 不处理多运单的智能排序或优先级逻辑

## Decisions

### 决策 1：新工具放在 `composite/` 层，不修改 domain 工具编排逻辑

**选择**：`TrackShipmentTool` 和 `ExpediteShipmentTool` 均作为 composite 工具，复用已有 domain 工具。  
**理由**：与 `CustomerServiceTicketTool` 保持一致的分层模式；domain 工具保持原子性，composite 层负责业务编排。  
**备选**：直接在 domain 层添加复合方法 → 否决，违反单一职责，domain 工具应保持 1:1 对应微服务接口。

### 决策 2：运单状态校验放在 `ExpediteShipmentTool` 中（composite 层）

**选择**：催件的 `IN_TRANSIT` 状态前置校验由 `ExpediteShipmentTool` 自身执行。  
**理由**：状态校验属于业务规则，不属于域能力；`ShipmentDomainTools.getShipment` 应保持纯查询语义。  
**备选**：在 domain 层新增 `validateShipmentForExpedite` → 否决，业务规则不应下沉到 domain 层。

### 决策 3：`listShipmentsByUser` 返回列表，由上层（智能体或 composite）决定使用哪条运单

**选择**：`listShipmentsByUser(userId)` 返回 `List<Shipment>`，不做筛选。  
**理由**：composite 层或智能体可按需选择（最新、在途中的等），domain 工具不假设业务意图。

### 决策 4：`ticketType` 向后兼容，`createTicket` 增加带默认值的重载或保持原签名不变

**选择**：`TicketDomainTools.createTicket` 增加 `ticketType` 参数，`CustomerServiceTicketTool` 传入 `"GENERAL"` 以保持原行为。  
**理由**：明确类型比依赖默认值更清晰；对现有行为零影响。

### 决策 5：`TrackShipmentTool` 无需身份验证，签名仅含 shipmentId

**选择**：`track_shipment(shipmentId)` 不需要 `userId`，直接查运单。  
**理由**：查件属于信息查询，无副作用；强制身份验证会增加调用摩擦而无安全收益。  

编排顺序：
```
getShipment(shipmentId)  // 查询运单，失败即终止
    ↓
return { status, route } // 直接返回 Shipment，不创建工单
```

### 决策 6：`ExpediteShipmentTool` 编排顺序 + 状态白名单校验

**催件允许状态（白名单）**：`PENDING_PICKUP`、`IN_TRANSIT`、`OUT_FOR_DELIVERY`、`EXCEPTION`  
**理由**：白名单策略对未来新增状态默认拒绝，更安全；黑名单策略会导致未知新状态被意外放行。  

编排顺序：
```
getUser(userId)                                          // 身份验证，失败即终止
    ↓
getShipment(shipmentId)                                  // 查询运单，失败即终止
    ↓
校验 status ∈ {PENDING_PICKUP, IN_TRANSIT,               // 业务校验，不通过返回
               OUT_FOR_DELIVERY, EXCEPTION}              // businessFailure(40001)
    ↓
createTicket(userId, shipmentId, content, "EXPEDITE")    // 创建催件工单
```

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|----------|
| Mock 数据不覆盖新场景（如 listShipmentsByUser） | 在 MockData 中为现有用户补充多条运单数据 |
| `ticketType` 引入后现有测试断言 Ticket 字段可能失效 | 更新 `CustomerServiceTicketTool` 相关测试，校验 ticketType == "GENERAL" |
| 催件对终态运单（DELIVERED）被调用 | `ExpediteShipmentTool` 明确校验，返回 businessFailure(40001, "运单状态不支持催件") |

### 决策 7：运单状态使用 `ShipmentStatus` enum，而非 String

**选择**：新增 `ShipmentStatus` enum，包含 `PENDING_PICKUP`、`IN_TRANSIT`、`OUT_FOR_DELIVERY`、`DELIVERED`、`RETURNED`、`EXCEPTION` 六个值；`Shipment` record 的 `status` 字段类型由 `String` 改为 `ShipmentStatus`。  
**理由**：白名单校验逻辑使用 enum + exhaustive switch，编译期即可发现遗漏状态，避免字符串拼写错误导致的静默 Bug；与白名单策略（决策 6）配合最自然。  
**备选**：保持 String → 否决，字符串白名单校验无编译期保障，教程代码应展示类型安全最佳实践。  
**转换规则**：domain 层从微服务（或 MockData）获取原始 String 后，使用 `ShipmentStatus.valueOf(str)` 转换；遇到未知值默认视为不可催件（与白名单策略一致）。

## Open Questions

~~- 查件是否需要身份验证？~~ **已确认**：不需要，`track_shipment` 签名仅含 `shipmentId`。  
~~- 催件 content 是固定模板还是由调用方传入？~~ **已确认**：由智能体（调用方）生成并传入。  
~~- 运单状态枚举是否需要定义为 Java enum？~~ **已确认**：定义为 `ShipmentStatus` enum，见决策 7。
