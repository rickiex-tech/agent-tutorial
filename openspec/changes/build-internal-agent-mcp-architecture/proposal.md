## Why

公司已有 200+ 业务 API（订单、运单、客服、客户、财务等）和 100+ Data API，按业务域微服务分类。要构建企业内部智能体，若把全部 300+ API 直接平铺暴露为 MCP tools，会导致工具爆炸（模型选择困难、Token 成本高、调用延迟大）、维护困难（API 与 Tool 定义不同步）、决策混乱（复杂工作流易出错）。需要一套分层、分域的 MCP 工具治理架构，让智能体面对少量高质量工具，而非 300+ 原始 API。

## What Changes

- 引入**三层 MCP 工具体系**，统一封装既有企业 API：
  - **业务编排工具层（Composite Tools）**：把稳定、固定、串行的业务流程封装为单个高层工具（如客服工单创建）。
  - **领域工具层（Domain Tools）**：按业务域封装微服务 API，可复用（如 `get_user`、`get_shipment`、`create_ticket`）。
  - **查询/数据工具层（Data Tools）**：单独治理 100+ Data API（如 `query_order`、`report_metrics`）。
- 引入 **Agent Runtime / Orchestrator**：负责任务理解、工具选择、工作流编排、会话状态、审计日志、权限上下文传递。
- 按**业务域**拆分 MCP 服务集群（业务域 MCP + 数据域 MCP），避免单体工具爆炸。
- 所有底层 API 调用统一经过 **API Gateway / 服务治理层**（认证、限流、熔断、路由、可观测性）。
- 定义**统一失败语义**：区分业务失败（终止，不重试）与系统失败（可重试）。
- 首批落地参考用例：**客服工单创建**（串行调用查询用户信息 → 查询运单信息 → 创建工单，强前置校验、失败即终止）。

## Capabilities

### New Capabilities

- `mcp-tool-architecture`: MCP 工具三层分层体系（业务编排 / 领域 / 数据）的封装规范、工具粒度划分、命名约定与暴露策略。
- `agent-orchestration`: Agent Runtime 的任务编排、工具选择、会话状态管理、审计与权限上下文传递。
- `customer-service-ticket`: 首批参考用例——客服工单创建的业务编排工具，串行流程编排与失败语义。
- `mcp-failure-semantics`: 统一的失败分类（业务失败 vs 系统失败）与重试/终止策略。

### Modified Capabilities

<!-- 无既有 spec 需要修改，全部为新增能力 -->

## Impact

- **新增系统组件**：Agent Runtime / Orchestrator、业务域 MCP 服务集群、数据域 MCP 服务集群、业务编排工具层。
- **受影响的既有系统**：200+ 业务 API + 100+ Data API（通过 MCP 工具间接调用，不修改既有 API 本身）。
- **统一接入治理层**：API Gateway / Service Mesh（认证、限流、熔断、可观测性）。
- **参考用例涉及的 API 端点**（客服工单创建）：
  - `GET /api/v1/users/{userId}`（查询用户信息）
  - `GET /api/v1/shipments/{shipmentId}`（查询运单信息）
  - `POST /api/v1/tickets`（创建工单）
- **数据库变更**：本架构层不直接产生 DDL/DML；既有微服务的表结构保持不变。如需新增 MCP 调用审计表（如 `agent_tool_invocation_log`、`agent_session`），将在 design.md 中定义。
- **对现有功能的影响**：智能体通过 MCP 工具只读/调用既有 API，不改变既有业务逻辑；新增治理层需评估限流与鉴权对既有 API 调用量的影响。
