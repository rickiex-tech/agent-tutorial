## ADDED Requirements

### Requirement: 三层 MCP 工具体系

系统 MUST 将既有企业 API 封装为三层 MCP 工具体系：业务编排工具层（Composite Tools）、领域工具层（Domain Tools）、数据工具层（Data Tools），而非将 300+ 原始 API 平铺暴露给智能体。

#### Scenario: 智能体仅看到高层工具

- **GIVEN** 系统已封装 300+ 既有 API
- **WHEN** 智能体加载可用工具列表
- **THEN** 智能体看到的是按层组织的少量高质量工具，而非 300+ 原始 API 端点

#### Scenario: 确定性流程下沉为业务编排工具

- **GIVEN** 一个稳定、固定、串行的业务流程（如客服工单创建）
- **WHEN** 该流程被封装
- **THEN** 系统 MUST 将其封装为单个业务编排工具，智能体只需调用一个工具即可完成

### Requirement: 领域工具按业务域封装

系统 MUST 按业务域（用户、运单、客服、财务、车队、站点等）将微服务 API 封装为可复用的领域工具，每个领域工具保持单一职责。

#### Scenario: 领域工具单一职责

- **GIVEN** 用户域微服务提供查询用户信息的 API
- **WHEN** 封装为领域工具 `get_user`
- **THEN** 该工具仅负责查询用户信息，不包含跨域编排逻辑

#### Scenario: 领域工具被复用

- **GIVEN** 领域工具 `get_user` 已存在
- **WHEN** 多个业务编排工具需要查询用户信息
- **THEN** 它们 MUST 复用同一个 `get_user` 领域工具，而非各自实现

### Requirement: 数据工具单独治理

系统 MUST 将 100+ Data API 封装为独立的数据工具层，与执行业务动作的领域工具分开组织与治理。

#### Scenario: 数据工具与动作工具分离

- **GIVEN** 一个查询类 Data API 和一个创建工单的业务动作 API
- **WHEN** 二者被封装为 MCP 工具
- **THEN** 查询类封装在数据工具层，业务动作封装在领域/编排工具层，分开治理

### Requirement: 工具命名与响应格式统一

系统 MUST 为所有 MCP 工具采用统一命名约定，并使用统一响应格式（包含 code、message、resultType、data）。

#### Scenario: 统一响应格式

- **GIVEN** 任意 MCP 工具被调用
- **WHEN** 工具返回结果
- **THEN** 响应 MUST 包含 `code`、`message`、`resultType`、`data` 字段
