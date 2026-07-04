## ADDED Requirements

### Requirement: 客服工单创建业务编排工具

系统 MUST 提供业务编排工具 `create_customer_service_ticket`，按固定串行顺序执行：查询用户信息 → 查询运单信息 → 创建工单，并具备强前置校验、失败即终止的语义。

#### Scenario: 正常路径成功创建工单

- **GIVEN** 用户信息存在且运单信息存在
- **WHEN** 调用 `create_customer_service_ticket(userId, shipmentId, content)`
- **THEN** 系统依次查询用户信息、查询运单信息、创建工单，并返回工单号 `ticketId`

#### Scenario: 用户信息查询失败则终止

- **WHEN** 第一步查询用户信息失败
- **THEN** 系统 MUST 立即终止流程，不执行后续运单查询与工单创建，并返回对应失败结果

#### Scenario: 运单不存在则终止

- **GIVEN** 用户信息查询成功
- **WHEN** 第二步查询运单信息返回不存在
- **THEN** 系统 MUST 立即终止流程，不创建工单，并返回业务失败（运单不存在）

#### Scenario: 创建工单失败则报错

- **GIVEN** 用户信息与运单信息均查询成功
- **WHEN** 第三步创建工单失败
- **THEN** 系统 MUST 返回工单创建失败错误

### Requirement: 串行执行顺序约束

系统 MUST 保证三个步骤严格串行执行，每一步成功后才进入下一步。

#### Scenario: 步骤间严格依赖

- **GIVEN** 流程包含查询用户、查询运单、创建工单三步
- **WHEN** 任意一步未成功完成
- **THEN** 系统 MUST NOT 执行其后续步骤
