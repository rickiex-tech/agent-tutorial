## Purpose

Define the Agent Runtime / Orchestrator responsibilities for task understanding, tool selection, workflow orchestration, LLM integration via MCP Client, and failure-semantic-driven user-facing behaviors.

---

## ADDED Requirements

### Requirement: Agent Runtime 任务编排

系统 MUST 提供 Agent Runtime / Orchestrator，负责任务理解、工具选择、工作流编排，将用户的自然语言请求转换为对 MCP 工具的调用。

#### Scenario: 将自然语言请求转换为工具调用

- **GIVEN** 用户发起一个物流业务请求
- **WHEN** Agent Runtime 接收请求
- **THEN** 系统 MUST 理解任务、选择合适的 MCP 工具并完成编排调用

#### Scenario: 优先选择业务编排工具

- **GIVEN** 存在覆盖该请求的业务编排工具
- **WHEN** Agent Runtime 选择工具
- **THEN** 系统 SHOULD 优先调用业务编排工具，而非自行拼装多个领域工具

### Requirement: 经 MCP Client 消费远程工具

Agent Runtime MUST 通过 MCP Client 连接 MCP Server，将远程工具注册为可供 LLM 自主调用的工具回调，并由云端 LLM（通义 OpenAI 兼容模式）决定调用时机与参数。

#### Scenario: 连接 MCP Server 并暴露工具

- **GIVEN** MCP Server 已注册若干 `@Tool`
- **WHEN** Agent Runtime 启动并建立 MCP Client 连接
- **THEN** 系统 MUST 将这些远程工具注册为 LLM 可调用的工具回调

#### Scenario: LLM 自主编排工具调用

- **GIVEN** 用户发起自然语言请求且存在可用工具
- **WHEN** Agent Runtime 将请求与工具一并交给 LLM
- **THEN** LLM MUST 自主决定是否调用工具及调用参数，系统据此经 MCP Client 执行调用并将结果回灌

### Requirement: 失败语义的用户可见行为

Agent Runtime MUST 依据工具返回的 `resultType` 决定向用户呈现的行为，且 MUST NOT 对业务失败重试或编造数据。

#### Scenario: 成功结果

- **GIVEN** 工具返回 `result_type = success`
- **WHEN** Agent Runtime 生成答复
- **THEN** 系统 MUST 用自然语言告知用户结果（如工单号）

#### Scenario: 业务失败由 LLM 解释

- **GIVEN** 工具返回 `result_type = business_failure`（如运单不存在）
- **WHEN** Agent Runtime 生成答复
- **THEN** 系统 MUST 将失败原因翻译为面向用户的解释，且 MUST NOT 重试或编造数据

#### Scenario: 系统失败提示稍后重试

- **GIVEN** 工具返回 `result_type = system_failure`
- **WHEN** Agent Runtime 生成答复
- **THEN** 系统 MUST 提示用户稍后重试，且 MUST NOT 反复自动调用同一工具

### Requirement: 会话状态管理

系统 MUST 管理智能体会话状态，持久化会话上下文以支持多轮交互。

#### Scenario: 持久化会话上下文

- **GIVEN** 用户与智能体进行多轮交互
- **WHEN** 每一轮交互完成
- **THEN** 系统 MUST 将会话状态与上下文持久化到 `agent_session` 表

### Requirement: 工具调用审计

系统 MUST 记录每一次 MCP 工具调用的审计日志，包含工具名、层级、参数、结果类型与耗时。

#### Scenario: 记录工具调用

- **GIVEN** 智能体调用任意 MCP 工具
- **WHEN** 调用完成（无论成功或失败）
- **THEN** 系统 MUST 向 `agent_tool_invocation_log` 写入一条记录，包含 result_type 与 duration_ms

#### Scenario: 敏感字段脱敏

- **GIVEN** 工具响应包含敏感字段（如客户隐私）
- **WHEN** 写入审计日志
- **THEN** 系统 MUST 对敏感字段脱敏，且 response_summary 只存摘要而非完整响应

### Requirement: 权限上下文传递

系统 MUST 在工具调用链中传递发起用户的权限上下文，底层 API 调用 MUST 经过 API Gateway 进行认证鉴权。

#### Scenario: 调用经过治理层鉴权

- **GIVEN** 智能体代表某用户调用底层 API
- **WHEN** MCP 工具发起调用
- **THEN** 调用 MUST 携带用户权限上下文并经过 API Gateway 认证鉴权
