## ADDED Requirements

### Requirement: 催件业务编排工具

系统 MUST 提供业务编排工具 `expedite_shipment`，按固定串行顺序执行：查询用户信息 → 查询运单信息 → 校验运单状态 → 创建催件工单，并具备强前置校验、失败即终止的语义。工单内容（content）由调用方（智能体）传入。

#### Scenario: 正常路径成功创建催件工单

- **GIVEN** 用户存在，运单存在且状态为可催件状态（`PENDING_PICKUP` / `IN_TRANSIT` / `OUT_FOR_DELIVERY` / `EXCEPTION`）
- **WHEN** 调用 `expedite_shipment(userId, shipmentId, content)`
- **THEN** 系统依次查询用户、查询运单、校验状态通过后创建 `ticketType=EXPEDITE` 的工单，并返回工单号 `ticketId`

#### Scenario: 用户不存在则终止

- **WHEN** 第一步查询用户信息失败
- **THEN** 系统 MUST 立即终止，不执行后续步骤，返回失败结果

#### Scenario: 运单不存在则终止

- **GIVEN** 用户信息查询成功
- **WHEN** 第二步查询运单信息返回不存在
- **THEN** 系统 MUST 立即终止，不执行后续步骤，返回业务失败

### Requirement: 催件运单状态前置校验

系统 MUST 在创建催件工单前校验运单状态。允许催件的状态（白名单）为：`PENDING_PICKUP`、`IN_TRANSIT`、`OUT_FOR_DELIVERY`、`EXCEPTION`。不在白名单内的状态（`DELIVERED`、`RETURNED` 及未来新增状态）MUST 被拒绝。

#### Scenario: 运单已签收时拒绝催件

- **GIVEN** 运单状态为 `DELIVERED`
- **WHEN** 调用 `expedite_shipment(userId, shipmentId, content)`
- **THEN** 系统 MUST 返回业务失败（错误码 40001，消息"运单状态不支持催件"），不创建工单

#### Scenario: 运单已退回时拒绝催件

- **GIVEN** 运单状态为 `RETURNED`
- **WHEN** 调用 `expedite_shipment(userId, shipmentId, content)`
- **THEN** 系统 MUST 返回业务失败（错误码 40001，消息"运单状态不支持催件"），不创建工单

#### Scenario: 可催件状态允许继续流程

- **GIVEN** 运单状态为 `PENDING_PICKUP`、`IN_TRANSIT`、`OUT_FOR_DELIVERY` 或 `EXCEPTION` 之一
- **WHEN** 状态校验执行
- **THEN** 系统 MUST 继续执行创建催件工单步骤
