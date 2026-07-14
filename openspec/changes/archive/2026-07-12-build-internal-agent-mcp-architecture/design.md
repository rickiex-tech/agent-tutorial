## Context

公司已有 200+ 业务 API 与 100+ Data API，按业务域微服务分类，技术栈为 Spring Boot 4.0.5 / Java 21 / MySQL 8.0 / Redis 7.0 / MyBatis。现在要构建企业内部智能体，让内部用户通过自然语言完成物流业务操作（如客服工单创建、运单查询、调度等）。

核心约束：
- 既有 API 不修改，智能体通过 MCP 工具间接调用。
- 不能把 300+ API 平铺暴露给模型（工具爆炸、Token 成本、决策错误）。
- 参考用例「客服工单创建」为强前置校验、串行、失败即终止的确定性流程。

## Goals / Non-Goals

**Goals:**
- 建立三层 MCP 工具体系：业务编排工具（Composite）、领域工具（Domain）、数据工具（Data）。
- 让智能体面对少量高质量工具，而非 300+ 原始 API。
- 把稳定的确定性工作流下沉为业务编排工具，统一失败语义。
- 按业务域拆分 MCP 服务，统一经过网关治理层（认证/限流/熔断/可观测性）。
- 提供首批参考实现：客服工单创建。

**Non-Goals:**
- 不修改既有 200+ 业务 API 与 100+ Data API 的实现。
- 不涉及 LLM 模型选型与训练（自托管 vs 云端在 Open Questions 中讨论）。
- 不覆盖前端 Agent UI 的具体交互设计。
- 不一次性封装全部 300+ API，按业务价值分批落地。

## Decisions

### 决策 1：三层工具体系而非平铺暴露

| 维度 | 平铺暴露 300+ API | 三层封装（采纳） |
|------|------------------|-----------------|
| 工具数量 | 300+ | 智能体面对数十个高层工具 |
| 模型选择难度 | 高 | 低 |
| Token 成本 | 高（全部 description） | 低 |
| 维护 | API 变更需逐个同步 | 按层隔离影响 |
| 复杂流程正确率 | 低（模型自拼） | 高（编排工具固化） |

**理由**：物流业务多为确定性流程，把固定链路固化为工具比让模型每次推理更稳、更省、更快。

**三层职责**：
- 业务编排工具层（Composite Tools）：封装稳定串行流程，统一失败语义，例如 `create_customer_service_ticket`。
- 领域工具层（Domain Tools）：按业务域封装微服务 API，可复用，例如 `get_user`、`get_shipment`、`create_ticket`。
- 数据工具层（Data Tools）：单独治理 100+ Data API，例如 `query_order`、`report_metrics`。

### 决策 2：MCP 服务按业务域拆分（微服务 MCP）而非单体

- 业务域 MCP 集群：用户域 / 运单域 / 客服域 / 财务域 / 车队域 / 站点域。
- 数据域 MCP 集群：经营数据 / 运营数据 / 报表 / 指标。

**理由**：独立扩展、独立发布；某域 API 升级只改对应 MCP，不影响其他域。**代价**：跨域编排由业务编排工具或 Orchestrator 负责。

### 决策 3：统一经过 API Gateway / Service Mesh

MCP 工具调用底层 API 时统一经过治理层：认证鉴权、限流、熔断降级、路由灰度、可观测性。**理由**：300+ API 的安全与稳定性必须集中治理，避免每个 MCP 自行实现。

### 决策 4：统一失败语义（业务失败 vs 系统失败）

- 业务失败（终止，不重试）：用户不存在、运单不存在、规则拒绝。
- 系统失败（可重试）：超时、上游异常、网络错误、依赖不可用。

**理由**：让智能体与人都能判断「该重试还是该终止」，避免对业务错误盲目重试。

**重试策略两层实现**：
1. **MCP Server 侧**：`SystemFailureRetryExecutor` 在工具执行层对 system_failure 进行内部重试（受控次数 + 熔断保护），对用户透明。
2. **Agent 侧**：由 system prompt 驱动，对 system_failure 告知用户"系统繁忙，请稍后重试"，**不自动重复调用**工具，由用户决定何时重新发起请求。两层策略互不冲突，分别保证内部稳定性和用户可见行为。

### 决策 5：客服工单创建作为首批业务编排工具

串行流程，强前置校验，失败即终止：

```
create_customer_service_ticket(userId, shipmentId, content)
  │
  ├─ 1. get_user(userId)          失败 → 终止（业务/系统失败分类返回）
  ├─ 2. get_shipment(shipmentId)  不存在 → 终止（业务失败）
  └─ 3. create_ticket(...)        失败 → 报错（最终失败）
  → 返回 ticketId
```

序列图：

```
智能体        Composite工具      用户域MCP     运单域MCP     客服域MCP
  │  调用工具     │                  │            │            │
  ├──────────────>│                  │            │            │
  │               │  get_user        │            │            │
  │               ├─────────────────>│            │            │
  │               │<─────────────────┤  用户信息  │            │
  │               │  get_shipment    │            │            │
  │               ├──────────────────────────────>│            │
  │               │<──────────────────────────────┤  运单信息  │
  │               │  create_ticket                              │
  │               ├──────────────────────────────────────────> │
  │               │<──────────────────────────────────────────┤ 工单号
  │<──────────────┤  返回 ticketId                              │
```

### 决策 6：审计与会话数据表设计

新增两张表用于审计与会话状态（遵循库表规范：蛇形命名、含 id/create_time/update_time）。

`agent_session`（智能体会话）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| session_id | VARCHAR(64) | 会话唯一标识 |
| user_id | BIGINT | 发起用户 |
| status | VARCHAR(32) | 会话状态 |
| context | JSON | 会话上下文 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

`agent_tool_invocation_log`（工具调用审计）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| session_id | VARCHAR(64) | 关联会话 |
| tool_name | VARCHAR(128) | 工具名 |
| tool_layer | VARCHAR(32) | composite/domain/data |
| request_params | JSON | 请求参数 |
| response_summary | JSON | 响应摘要 |
| result_type | VARCHAR(32) | success/business_failure/system_failure |
| duration_ms | BIGINT | 耗时 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

### 决策 7：统一工具响应格式

复用既有 `Result（code, message, data）` 风格，工具响应附带失败分类：

```json
{
  "code": 0,
  "message": "success",
  "resultType": "success",
  "data": { "ticketId": 123456 }
}
```

失败示例：

```json
{
  "code": 40401,
  "message": "shipment not found",
  "resultType": "business_failure",
  "data": null
}
```

### 决策 8：Agent 消费侧 = 独立模块 + 云 LLM（通义）+ MCP Client，失败语义纯 prompt 驱动

智能体消费侧（`logistics-agent`）作为**独立 Spring Boot 模块**（不与 `logistics-mcp-server` 共享 parent），通过 Spring AI 的 MCP Client 连接 MCP Server，由云端 LLM 自主编排工具。

**形态与选型：**

| 维度 | 选择 | 理由 |
|------|------|------|
| 入口形态 | REST 服务 `POST /api/v1/agent/chat` | 最接近生产、便于被前端/其他系统调用 |
| LLM | 通义千问（OpenAI 兼容模式）`qwen-plus` | 国内直连、成本低、原生支持 function calling；可一行配置切 `qwen-max`/`qwen-turbo` |
| 兼容端点 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | 复用 Spring AI 官方 OpenAI starter，避免第三方 starter 版本错配 |
| 模块组织 | 独立模块（无聚合 parent） | 两模块可独立演进、互不影响版本 |
| 端口 | agent `8081` / server `8080` | 避免端口冲突 |
| API Key | 环境变量 `DASHSCOPE_API_KEY` 注入 | 不入代码库，降低泄露风险 |

**链路：**

```
POST /api/v1/agent/chat {"message":"用户1001运单9001破损，建个工单"}
  → ChatClient.prompt().system(失败语义).user(message).tools(mcpToolCallbacks)
  → 通义 qwen ─决策─▶ tool_call: createCustomerServiceTicket(1001,9001,"破损")
  → MCP Client ──SSE──▶ logistics-mcp-server(8080) 执行
  → ToolResult{resultType,...} 回灌 ─▶ 通义生成自然语言答复
  → {"reply":"已为您创建工单 70001 ..."}
```

**失败语义透传策略——纯 prompt 驱动，agent 侧零重试代码：**

MCP 工具返回的 `ToolResult`（含 `resultType`）原样回灌给 LLM，由 system prompt 规定 LLM 对三类结果的可见行为：

| resultType | LLM 行为 | 是否重试 |
|-----------|---------|---------|
| `SUCCESS` | 用自然语言告知结果（如工单号） | — |
| `BUSINESS_FAILURE` | 把 message 翻译成人话解释（如"未找到运单，请核对单号"），不编造数据 | 否 |
| `SYSTEM_FAILURE` | 告知"系统繁忙，请稍后重试"，不反复调用 | 否（由用户决定） |

**理由**：失败语义的「机制」固化在 MCP Server（决策 4），「策略」放在 agent 的 prompt——换策略只改 prompt，不动代码，与三层工具架构的解耦哲学一致。

## Risks / Trade-offs

- [业务编排工具层维护成本] → 仅对稳定、高频、确定性流程做编排封装，低频/探索性任务交给 Orchestrator 动态组合领域工具。
- [跨域 MCP 编排复杂度上升] → 由业务编排工具或 Orchestrator 统一承担，领域工具保持单一职责。
- [治理层成为单点/瓶颈] → 网关需高可用部署 + 缓存热点数据（Redis），并对 Data API 设独立限流。
- [工具与底层 API 漂移] → 用 OpenAPI/Swagger 自动生成领域工具骨架，CI 校验契约一致性。
- [审计 JSON 字段膨胀] → response_summary 只存摘要而非完整响应，敏感字段脱敏。
- [模型仍可能选错工具] → 工具命名/description 标准化 + 按域分组 + 少量高层工具优先暴露。

## Migration Plan

1. 搭建 Agent Runtime / Orchestrator 与治理层接入。
2. 先封装客服域、用户域、运单域的领域工具（覆盖参考用例）。
3. 落地首个业务编排工具 `create_customer_service_ticket`。
4. 建立审计与会话表，接入可观测性。
5. 按业务价值分批扩展其余业务域与数据域工具。
- 回滚策略：MCP 工具为旁路调用既有 API，停用 MCP 服务即可回退，不影响既有业务系统。

## Open Questions

- LLM 选型：参考实现采用云端通义（OpenAI 兼容，见决策 8）验证链路；企业生产环境的自托管开源模型 vs 云端 API（数据合规、成本、延迟权衡）仍待评估。
- 业务编排工具与 Orchestrator 动态编排的边界如何随业务演进调整。
- 权限模型：按用户/部门的工具级与数据级权限如何细粒度控制。
- 缓存策略：哪些 Data API 结果可缓存、TTL 多长。
