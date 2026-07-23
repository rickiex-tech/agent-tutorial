## Purpose

Define the shipment tracking composite tool as a reference implementation of a read-only business orchestration tool, demonstrating direct domain tool delegation and business failure propagation without user identity verification.

---

## ADDED Requirements

### Requirement: 查件业务编排工具

系统 MUST 提供业务编排工具 `track_shipment`，直接查询运单信息并返回运单状态与路由，无需用户身份验证。

#### Scenario: 正常路径成功返回运单信息

- **GIVEN** 运单信息存在
- **WHEN** 调用 `track_shipment(shipmentId)`
- **THEN** 系统查询运单信息，并返回运单的 `status` 和 `route`

#### Scenario: 运单不存在则返回业务失败

- **WHEN** 查询运单信息返回不存在
- **THEN** 系统 MUST 返回业务失败（运单不存在），不继续处理

### Requirement: 按用户查询运单列表

系统 MUST 提供领域工具 `list_shipments_by_user`，支持按用户 ID 查询该用户名下所有运单。

#### Scenario: 返回用户名下所有运单

- **GIVEN** 用户名下存在一条或多条运单
- **WHEN** 调用 `list_shipments_by_user(userId)`
- **THEN** 系统返回该用户所有运单的列表（含 status、route 等字段）

#### Scenario: 用户无运单时返回空列表

- **GIVEN** 用户名下不存在任何运单
- **WHEN** 调用 `list_shipments_by_user(userId)`
- **THEN** 系统返回空列表，而非失败结果
