# agent-tutorial — 企业内部智能体参考实现

这是一个基于 **Spring AI 2.0** 和 **MCP（Model Context Protocol）** 的企业内部智能体架构参考实现。通过完整的端到端链路演示，验证 LLM + MCP Tools 的企业落地可行性。

> 🎯 核心演示：智能客服助手通过 MCP Tools 与物流系统交互，完成查件、催件、工单创建等复杂业务流程。

## 项目结构

```
agent-tutorial/
├── logistics-agent/           # 智能体应用（LLM + MCP 客户端）
│   ├── src/main/java/.../
│   │   ├── service/AgentService       # 核心服务：ChatClient + MCP Tools + userId 上下文注入
│   │   ├── web/AgentController        # REST 接口
│   │   ├── security/                  # 权限上下文 + MCP 鉴权 Header 透传
│   │   ├── session/                   # 多轮会话持久化
│   │   └── audit/                     # 工具调用审计
│   └── src/main/resources/
│       └── application.yml            # 通义千问 + Streamable HTTP 配置
│
├── logistics-mcp-server/      # MCP 服务器（工具暴露层）
│   ├── src/main/java/.../
│   │   ├── tools/composite/           # 业务编排工具（查件/催件/建工单，串行 + 失败即终止）
│   │   ├── tools/domain/               # 领域工具（用户/运单/客服域）
│   │   ├── tools/data/                 # 数据域工具（运营指标查询）
│   │   ├── tools/BusinessTool.java     # 标记接口：业务工具自动扫描
│   │   ├── tools/DataTool.java         # 标记接口：数据工具自动扫描
│   │   ├── ToolExposureProperties.java # 工具暴露开关（business-enabled / data-enabled）
│   │   └── common/                     # 统一响应格式 + 失败语义 + 重试/熔断
│   └── src/test/java/                 # 完整的单元测试覆盖
│
├── docs/                      # 命名与封装规范等文档
└── openspec/                  # OpenSpec 变更管理（规划、设计、任务）
    └── changes/
        ├── customer-service-shipment-tools/   # 当前变更：查件/催件/建工单工具
        └── archive/                            # 已归档变更
```

## 快速开始

### 前置条件

- Java 21: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home`
- DashScope API Key（通义千问）: `export DASHSCOPE_API_KEY=<your-key>`
- Maven 3.8+

### 运行

```bash
# 1️⃣ 启动 MCP Server（工具暴露）
cd logistics-mcp-server
mvn spring-boot:run              # 默认端口 8080

# 2️⃣ 启动 Agent 应用（另一个终端）
cd logistics-agent
mvn spring-boot:run              # 默认端口 8081

# 3️⃣ 发送请求（完整测试用例见 logistics-agent/requests.http）
curl -X POST http://localhost:8081/api/v1/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"sessionId":"sess-track-9001","userId":1001,"roles":"agent-user","message":"帮我查一下运单9001现在到哪了"}'

# 催件（需要 userId，由 AgentService 自动注入到 LLM 上下文）
curl -X POST http://localhost:8081/api/v1/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"sessionId":"sess-expedite-9001","userId":1001,"roles":"agent-user","message":"运单9001太慢了，帮我加急一下"}'
```

### 测试

```bash
# MCP Server 单元测试（覆盖 4 个场景）
cd logistics-mcp-server
mvn test

# Agent 集成测试
cd logistics-agent
mvn test
```

## 核心架构

```
┌─────────────────────────────────────────────────┐
│  用户界面 (Web API)                             │
│  POST /api/v1/agent/chat {"message": "..."}    │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│  智能体编排引擎 (AgentService)                  │
│  • ChatClient (通义千问)                         │
│  • MCP Tool 调用决策                            │
│  • userId 上下文注入（PermissionContext → prompt）│
│  • 失败语义处理（prompt 驱动）                  │
└────────────────┬────────────────────────────────┘
                 │ Streamable HTTP (MCP 协议 2025-11-25)
                 │ + X-User-Id / X-Roles / X-Session-Id Header
┌────────────────▼────────────────────────────────┐
│  MCP Tools 层（logistics-mcp-server）           │
│  ├─ 业务编排工具（implements BusinessTool）     │
│  │  ├─ trackShipment（查件：只需运单 ID）       │
│  │  ├─ expediteShipment（催件：用户→运单→工单）│
│  │  └─ createCustomerServiceTicket（建工单）    │
│  │     （串行：用户 → 运单 → 工单）            │
│  │                                              │
│  ├─ 领域工具（implements BusinessTool）         │
│  │  ├─ getUser (用户域)                        │
│  │  ├─ getShipment (运单域)                    │
│  │  └─ createTicket (客服域)                   │
│  │                                              │
│  └─ 数据工具（implements DataTool）             │
│     └─ getOrderMetrics (运营指标)              │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│  Mock 数据层 (真实环境 → 真实 API)              │
│  • USERS: {1001: VIP, 1002: NORMAL, 1003: NORMAL}│
│  • SHIPMENTS: {9001: 运输中, 9002: 已送达, ...} │
│  • 故障模拟: userId=1500 用户超时, userId=1002 工单不可用 │
└─────────────────────────────────────────────────┘
```

## 关键特性

### 1. 统一工具响应格式
所有工具返回 `ToolResult<T>`，包含：
- `code`: 业务码
- `message`: 错误或成功描述
- `resultType`: SUCCESS / BUSINESS_FAILURE / SYSTEM_FAILURE
- `data`: 响应数据

### 2. 失败语义分类（无 Agent 端重试代码）
| resultType | 含义 | 用户可见行为 |
|---|---|---|
| `SUCCESS` | 成功 | 返回结果 |
| `BUSINESS_FAILURE` | 业务规则失败（如用户不存在） | 向用户解释失败原因，禁止重试 |
| `SYSTEM_FAILURE` | 系统故障（超时/不可用） | "系统繁忙，请稍后重试" |

### 3. 业务编排 + 失败即终止
三个业务编排工具内部串行编排领域工具，任意一步失败立即终止，无需 agent 侧处理：
- **trackShipment**（查件）：查询运单 → 返回状态与路由
- **expediteShipment**（催件）：查询用户 → 查询运单 → 校验状态白名单 → 创建催件工单
- **createCustomerServiceTicket**（建工单）：查询用户 → 查询运单 → 创建客服工单

### 4. 标记接口自动扫描
新增工具无需手动注册。实现 `BusinessTool` 或 `DataTool` 标记接口并标注 `@Service` + `@Tool`，
Spring 会自动注入 `List<BusinessTool>` / `List<DataTool>` 到 `@Bean` 方法，完成 MCP 工具注册。

### 5. 工具暴露开关（ToolExposureProperties）
通过 `logistics.mcp.tools.business-enabled` / `data-enabled` 控制工具分组暴露，
支持按 profile 独立部署：
- `default` profile：业务 + 数据工具都暴露（端口 8080）
- `business` profile：仅业务工具（端口 8080）
- `data` profile：仅数据工具（端口 8082）

### 6. userId 上下文注入
`AgentController` 把请求中的 `userId` 放入 `PermissionContext`（ThreadLocal），
`AgentService.chat()` 将其作为上下文前缀注入到用户消息，使 LLM 调用需要 userId 的工具（催件、建工单）时
能直接使用，避免从自然语言中提取或编造。

### 7. Streamable HTTP 传输
MCP 传输协议已迁移到新的 **Streamable HTTP**（SSE 已弃用），端点为 `/mcp`，默认端口 8080。

### 8. 端到端鉴权链路
Agent 侧通过 `McpClientAuthHeaderConfig` 自动把 `PermissionContext` 透传为 HTTP Header（`X-User-Id` / `X-Roles` / `X-Session-Id`），MCP Server 的 `GatewayGovernanceFilter` 对所有 `/mcp` 流量做鉴权（`require-auth-header: true`），无需豁免路径。握手阶段注入 `anonymous` 保证连通性。

## 技术栈

- **Java 21** / Spring Boot 4.1.0 / Spring AI 2.0.0
- **LLM**: 通义千问 (Dashscope OpenAI 兼容模式)
- **MCP**: spring-ai-starter-mcp-server-webmvc + spring-ai-starter-mcp-client-webflux
- **传输**: Streamable HTTP (MCP 协议 2025-11-25)
- **测试**: JUnit 5 + Mockito

## 模块详解

### [logistics-mcp-server](logistics-mcp-server/README.md)
MCP 工具暴露层，包含：
- 业务编排工具（1 个）
- 领域工具（3 个：用户、运单、客服）
- 统一响应格式 + 失败语义
- 完整的单元测试（4 个场景覆盖）

### [logistics-agent](logistics-agent/README.md)
智能体应用，包含：
- `AgentService`: 集成 ChatClient + ToolCallbackProvider
- `AgentController`: REST 接口 (`POST /api/v1/agent/chat`)
- System Prompt: 驱动三类失败语义的用户可见行为
- 单元测试

## 测试覆盖

### 单元测试

| 场景 | MCP Server | Agent |
|------|-----------|-------|
| 正常路径 | ✅ 创建工单成功 | ✅ 返回工单号 |
| 用户不存在 | ✅ BUSINESS_FAILURE | ✅ 告知用户核实信息 |
| 运单不存在 | ✅ BUSINESS_FAILURE | ✅ 告知用户核实信息 |
| 系统故障 | ✅ SYSTEM_FAILURE | ✅ "系统繁忙，请稍后重试" |

### 端到端测试（requests.http）

`logistics-agent/requests.http` 提供完整的端到端测试用例，覆盖三类业务工具的成功与失败路径：

**查件（trackShipment）** — T1-T5：

| 用例 | 运单 | 状态 | 预期 |
|------|------|------|------|
| T1 | 9001 | IN_TRANSIT | 成功，返回状态与路由 |
| T2 | 9003 | PENDING_PICKUP | 成功，返回状态与路由 |
| T3 | 9005 | OUT_FOR_DELIVERY | 成功，返回状态与路由 |
| T4 | 9006 | EXCEPTION | 成功，返回状态与路由 |
| T5 | 9999 | 不存在 | 业务失败，引导核对 |

**催件（expediteShipment）** — E1-E9：

| 用例 | 用户 | 运单 | 状态 | 预期 |
|------|------|------|------|------|
| E1 | 1001 | 9001 | IN_TRANSIT | 成功，创建催件工单 |
| E2 | 1001 | 9003 | PENDING_PICKUP | 成功，创建催件工单 |
| E3 | 1003 | 9005 | OUT_FOR_DELIVERY | 成功，创建催件工单 |
| E4 | 1003 | 9006 | EXCEPTION | 成功，创建催件工单 |
| E5 | 1001 | 9999 | 不存在 | 业务失败，引导核对 |
| E6 | 1003 | 9002 | DELIVERED | 业务失败，状态不支持催件 |
| E7 | 1001 | 9004 | RETURNED | 业务失败，状态不支持催件 |
| E8 | 1500 | 9001 | IN_TRANSIT | 系统失败，用户服务超时 |
| E9 | 1002 | 9005 | OUT_FOR_DELIVERY | 系统失败，工单服务不可用 |

## 设计文档

- 已归档的基础架构变更：[`openspec/changes/archive/2026-07-12-build-internal-agent-mcp-architecture/`](openspec/changes/archive/2026-07-12-build-internal-agent-mcp-architecture/)
- 当前查件/催件/建工单工具变更：[`openspec/changes/customer-service-shipment-tools/`](openspec/changes/customer-service-shipment-tools/)
- 工具命名与封装规范：[`docs/tool-naming-and-encapsulation.md`](docs/tool-naming-and-encapsulation.md)

## 环境变量

```bash
# 必需
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
export DASHSCOPE_API_KEY=<your-dashscope-api-key>

# 可选
export AGENT_PORT=8081          # 默认 8081
export MCP_SERVER_PORT=8080     # 默认 8080
```

## 常见问题

**Q: 如何添加新的 MCP 工具？**  
A: 在 `logistics-mcp-server/tools/domain/` 或 `tools/composite/` 新建工具类，实现 `BusinessTool`（业务工具）或 `DataTool`（数据工具）标记接口，标注 `@Service` + `@Tool` 即可。Spring 会自动扫描并注册，无需修改 `@Bean` 方法。

**Q: 如何控制工具分组暴露？**  
A: 通过 `logistics.mcp.tools.business-enabled` / `data-enabled` 配置项控制。可按 profile 独立部署：`default`（都暴露）、`business`（仅业务）、`data`（仅数据）。

**Q: Agent 如何获取 userId？**  
A: `AgentController` 从请求中提取 `userId` 放入 `PermissionContext`（ThreadLocal），`AgentService.chat()` 将其作为上下文前缀注入到用户消息，LLM 调用需要 userId 的工具时直接使用，避免从自然语言中提取或编造。

**Q: 如何切换 LLM 模型？**  
A: 修改 `logistics-agent/application.yml` 中的 `spring.ai.openai.chat.model`，支持任何 OpenAI 兼容的 API。

**Q: Agent 如何处理工具调用失败？**  
A: 完全由 system prompt 驱动，无需编写重试代码。工具返回的 `resultType` 和 `message` 直接回灌给 LLM，LLM 根据 prompt 决定用户可见的回复。

## 许可证

MIT
