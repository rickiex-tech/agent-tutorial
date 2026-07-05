# agent-tutorial — 企业内部智能体参考实现

这是一个基于 **Spring AI 2.0** 和 **MCP（Model Context Protocol）** 的企业内部智能体架构参考实现。通过完整的端到端链路演示，验证 LLM + MCP Tools 的企业落地可行性。

> 🎯 核心演示：智能客服助手通过 MCP Tools 与物流系统交互，完成用户查询、工单创建等复杂业务流程。

## 项目结构

```
agent-tutorial/
├── logistics-agent/           # 智能体应用（LLM + MCP 客户端）
│   ├── src/main/java/.../
│   │   ├── AgentService       # 核心服务：集成 ChatClient + MCP Tools
│   │   └── AgentController    # REST 接口
│   └── src/main/resources/
│       └── application.yml    # 通义千问 + Streamable HTTP 配置
│
├── logistics-mcp-server/      # MCP 服务器（工具暴露层）
│   ├── src/main/java/.../
│   │   ├── tools/composite/   # 业务编排工具（串行 + 失败即终止）
│   │   ├── tools/domain/      # 领域工具（按业务域封装）
│   │   └── common/            # 统一响应格式 + 失败语义
│   └── src/test/java/         # 完整的单元测试覆盖
│
└── openspec/                  # OpenSpec 变更管理（规划、设计、任务）
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

# 3️⃣ 发送请求
curl -X POST http://localhost:8081/api/v1/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"帮我查一下用户1001的运单9001现在到哪了"}'
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
│  • 失败语义处理（prompt 驱动）                  │
└────────────────┬────────────────────────────────┘
                 │ Streamable HTTP (MCP 协议 2025-11-25)
┌────────────────▼────────────────────────────────┐
│  MCP Tools 层（logistics-mcp-server）           │
│  ├─ 业务编排工具                               │
│  │  └─ createCustomerServiceTicket              │
│  │     （串行：用户 → 运单 → 工单）            │
│  │                                              │
│  └─ 领域工具                                   │
│     ├─ getUser (用户域)                        │
│     ├─ getShipment (运单域)                    │
│     └─ createTicket (客服域)                   │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│  Mock 数据层 (真实环境 → 真实 API)              │
│  • USERS: {1001: VIP, 1002: NORMAL, ...}       │
│  • SHIPMENTS: {9001: 运输中, 9002: 已送达, ...}│
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
`createCustomerServiceTicket` 内部串行编排三个领域工具，任意一步失败立即终止，无需 agent 侧处理。

### 4. Streamable HTTP 传输
MCP 传输协议已迁移到新的 **Streamable HTTP**（SSE 已弃用），端点为 `/mcp`，默认端口 8080。

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

| 场景 | MCP Server | Agent |
|------|-----------|-------|
| 正常路径 | ✅ 创建工单成功 | ✅ 返回工单号 |
| 用户不存在 | ✅ BUSINESS_FAILURE | ✅ 告知用户核实信息 |
| 运单不存在 | ✅ BUSINESS_FAILURE | ✅ 告知用户核实信息 |
| 系统故障 | ✅ SYSTEM_FAILURE | ✅ "系统繁忙，请稍后重试" |

## 设计文档

完整的设计决策、架构图、失败语义等说明见 [`openspec/changes/build-internal-agent-mcp-architecture/`](openspec/changes/build-internal-agent-mcp-architecture/)。

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
A: 在 `logistics-mcp-server/tools/domain/` 新建 `*DomainTools.java`，用 `@Tool` 注解暴露方法，确保返回 `ToolResult<T>` 即可。

**Q: 如何切换 LLM 模型？**  
A: 修改 `logistics-agent/application.yml` 中的 `spring.ai.openai.chat.model`，支持任何 OpenAI 兼容的 API。

**Q: Agent 如何处理工具调用失败？**  
A: 完全由 system prompt 驱动，无需编写重试代码。工具返回的 `resultType` 和 `message` 直接回灌给 LLM，LLM 根据 prompt 决定用户可见的回复。

## 许可证

MIT
