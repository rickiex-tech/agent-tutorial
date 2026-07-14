## ADDED Requirements

### Requirement: 失败分类

系统 MUST 将工具调用结果分为业务失败（business_failure）与系统失败（system_failure）两类，并在响应的 `resultType` 字段中明确标识。

#### Scenario: 业务失败标识

- **GIVEN** 调用因业务规则失败（如用户不存在、运单不存在、规则拒绝）
- **WHEN** 工具返回结果
- **THEN** 响应的 `resultType` MUST 为 `business_failure`

#### Scenario: 系统失败标识

- **GIVEN** 调用因系统原因失败（如超时、上游异常、网络错误、依赖不可用）
- **WHEN** 工具返回结果
- **THEN** 响应的 `resultType` MUST 为 `system_failure`

### Requirement: 重试与终止策略

系统 MUST 对业务失败终止且不重试，对系统失败允许重试。

#### Scenario: 业务失败不重试

- **GIVEN** 一次调用返回 `business_failure`
- **WHEN** 智能体或编排逻辑处理该结果
- **THEN** 系统 MUST 终止该流程，且 MUST NOT 对该业务失败发起重试

#### Scenario: 系统失败可重试

- **GIVEN** 一次调用返回 `system_failure`
- **WHEN** 智能体或编排逻辑处理该结果
- **THEN** 系统 MAY 在受控重试策略下重试该调用
