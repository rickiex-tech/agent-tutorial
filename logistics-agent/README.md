# logistics-agent

物流智能体**消费侧**参考实现。通过 Spring AI 的 **MCP Client** 连接 [`logistics-mcp-server`](../logistics-mcp-server)，
把远程 MCP 工具注册为大模型可调用的工具，由**通义千问**自主决定何时、以何参数调用工具，并依据工具返回的
`resultType` 生成面向用户的自然语言答复。

对应 OpenSpec 变更 `build-internal-agent-mcp-architecture` 的设计决策 8，以及
`agent-orchestration` 规格中"经 MCP Client 消费远程工具""失败语义的用户可见行为"两项需求。

## 架构

```
用户 → POST /api/v1/agent/chat
            │
       AgentController（设置 PermissionContext: userId/roles/sessionId）
            │
        AgentService ── ChatClient(通义 qwen) ──┐
            │   注入 userId 上下文到 prompt       │ 自主编排
      └──► MCP Client (Streamable HTTP) ──┘
                       │  + X-User-Id / X-Roles / X-Session-Id Header
        http://localhost:8080
                       │
              logistics-mcp-server（工具：trackShipment / expediteShipment / createCustomerServiceTicket 等）
```

- 可编辑架构图：[`logistics-agent-architecture.drawio`](./logistics-agent-architecture.drawio)
- **LLM**：通义千问，走 OpenAI 兼容模式（`https://dashscope.aliyuncs.com/compatible-mode/v1`）。
- **工具来源**：MCP Client 经 Streamable HTTP 发现 server 暴露的工具并注册为 `ToolCallback`。
- **userId 上下文注入**：`AgentController` 把请求中的 `userId` 放入 `PermissionContext`（ThreadLocal），
  `AgentService.chat()` 将其作为上下文前缀注入到用户消息，使 LLM 调用需要 userId 的工具（催件、建工单）时
  能直接使用，避免从自然语言中提取或编造。
- **失败语义纯 prompt 驱动**：agent 侧不写任何重试/分支代码，工具返回的 `resultType` 原样回灌给 LLM，
  由 system prompt 规定三类结果的用户可见行为。

| resultType        | 含义           | 用户可见行为                          |
| ----------------- | -------------- | ------------------------------------- |
| `SUCCESS`         | 成功           | 自然语言告知结果（如工单号）          |
| `BUSINESS_FAILURE`| 业务规则失败   | 解释原因、引导核对，**不重试**        |
| `SYSTEM_FAILURE`  | 系统临时故障   | "系统繁忙，请稍后重试"，**不重试**    |

### System Prompt（失败语义 + 工具选择）

[AgentService](src/main/java/com/logistics/agent/service/AgentService.java) 的 system prompt 规定：

1. **工具选择指南**：
   - 用户查询运单状态/路由 → 调用 `trackShipment`
   - 用户要求催件/加急 → 调用 `expediteShipment`（需 userId + shipmentId）
   - 用户要求创建客服工单 → 调用 `createCustomerServiceTicket`（需 userId + shipmentId + content）
2. **失败语义**：工具返回 `resultType` 字段，agent 必须按以下规则响应：
   - `SUCCESS`：从 `data` 提取关键字段，用自然语言总结给用户
   - `BUSINESS_FAILURE`：从 `errorMessage` 提取原因，用业务语言解释，**不重试**
   - `SYSTEM_FAILURE`：告知用户"系统繁忙，请稍后重试"，**不重试**（重试由工具内部完成）
3. **用户身份**：用户消息开头会带 `【当前用户上下文】userId=xxx`，调用需要 userId 的工具时直接使用此值，
   不要从用户消息中提取或编造 userId。

### userId 上下文注入

```java
// AgentController：从请求中提取 userId，放入 PermissionContext
PermissionContext ctx = new PermissionContext(sessionId, request.userId(), roles);
PermissionContextHolder.set(ctx);
try {
    return agentService.chat(request);
} finally {
    PermissionContextHolder.clear();
}

// AgentService.chat()：注入上下文到 prompt
PermissionContext ctx = PermissionContextHolder.get();
String userPrompt = "【当前用户上下文】userId=" + ctx.userId() + "\n\n" + message;
```

- `PermissionContext` 为 ThreadLocal，确保异步线程隔离。
- LLM 看到上下文前缀后，调用 `expediteShipment` / `createCustomerServiceTicket` 时直接使用该 userId，
  避免从自然语言中提取或编造。
- 真实环境中可替换为从 JWT/OAuth token 解析，注入链路保持不变。

## 前置条件

- JDK 21（构建需 `JAVA_HOME` 指向 JDK 21）
- 运行中的 `logistics-mcp-server`（默认 `http://localhost:8080`）
- 通义 API Key，通过环境变量 `DASHSCOPE_API_KEY` 注入

> 当前默认模型为 `qwen-plus`。如需切换，可修改 `application.yml` 中的 `spring.ai.openai.chat.model`。

## 构建与测试

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
cd logistics-agent
mvn -B clean test
```

控制器测试 `AgentControllerTest` 使用 mock 的 `AgentService`，**不依赖** LLM、API Key 或运行中的 server，
用于在 CI 中稳定验证 HTTP 契约。

## 端到端验证（手动）

需要真实的 API Key 与运行中的 server。

1. 启动 MCP server：

   ```bash
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
   cd logistics-mcp-server && mvn spring-boot:run
   ```

2. 另开终端，设置 Key 并启动 agent：

   ```bash
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
   export DASHSCOPE_API_KEY=sk-xxxxxxxx
   cd logistics-agent && mvn spring-boot:run
   ```

3. 用 curl 触达核心场景（mock 数据见 server 的 `MockData`）：

   ```bash
   # T1 查件成功：运单 9001 → 运输中，北京→上海
   curl -s -X POST http://localhost:8081/api/v1/agent/chat \
     -H 'Content-Type: application/json' \
     -d '{"sessionId":"sess-1001","userId":1001,"roles":"agent-user","message":"帮我查一下运单9001的状态"}'

   # T5 查件运单不存在：运单 9999 → 未查询到相关信息
   curl -s -X POST http://localhost:8081/api/v1/agent/chat \
     -H 'Content-Type: application/json' \
     -d '{"sessionId":"sess-1001","userId":1001,"roles":"agent-user","message":"帮我查一下运单9999的状态"}'

   # E1 催件成功：用户 1001 + 运单 9001 → 创建催件工单
   curl -s -X POST http://localhost:8081/api/v1/agent/chat \
     -H 'Content-Type: application/json' \
     -d '{"sessionId":"sess-1001","userId":1001,"roles":"agent-user","message":"运单9001太慢了，帮我加急一下"}'

   # E6 催件业务失败：用户 1003 + 运单 9002（已签收）→ 已签收，不支持加急
   curl -s -X POST http://localhost:8081/api/v1/agent/chat \
     -H 'Content-Type: application/json' \
     -d '{"sessionId":"sess-1003","userId":1003,"roles":"agent-user","message":"运单9002太慢了，帮我加急一下"}'

   # E8 催件系统失败：用户 1500 上游不可用 → 系统繁忙，请稍后重试
   curl -s -X POST http://localhost:8081/api/v1/agent/chat \
     -H 'Content-Type: application/json' \
     -d '{"sessionId":"sess-1500","userId":1500,"roles":"agent-user","message":"运单9001太慢了，帮我加急一下"}'
   ```

预期：T1 返回运单状态；T5 解释运单不存在；E1 给出工单号；E6 解释已签收不支持加急；E8 提示"系统繁忙，请稍后重试"。

完整端到端用例（T1-T5 查件、E1-E9 催件）见 [`requests.http`](./requests.http)，可在 VS Code REST Client 中直接发送。

## 配置一览（`application.yml`）

| 配置项                                                    | 值                                                  |
| -------------------------------------------------------- | --------------------------------------------------- |
| `server.port`                                            | `8081`                                              |
| `spring.ai.openai.base-url`                              | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| `spring.ai.openai.api-key`                               | `${DASHSCOPE_API_KEY:}`                             |
| `spring.ai.openai.chat.model`                            | `qwen-plus`                                         |
| `spring.ai.openai.chat.temperature`                      | `0.1`                                               |
| `spring.ai.mcp.client.type`                              | `SYNC`                                              |
| `spring.ai.mcp.client.request-timeout`                   | `30s`                                               |
| `spring.ai.mcp.client.streamable-http.connections.logistics-server.url` | `http://localhost:8080`                 |
| `spring.ai.mcp.client.toolcallback.enabled`              | `true`                                              |

## 会话与上下文

`POST /api/v1/agent/chat` 支持以下字段：

- `message`：用户输入（必填）
- `sessionId`：会话 ID（建议传入）
- `userId`：发起用户 ID（用于权限上下文）
- `roles`：用户角色（用于权限上下文）

系统默认将多轮上下文持久化到 MySQL 的 `agent_session`，并将运行审计写入 `agent_tool_invocation_log`。如需本地回退为内存实现，可设置 `LOGISTICS_PERSISTENCE_IN_MEMORY_ENABLED=true`。

## MCP 鉴权 Header 透传

Agent 侧通过 `McpClientAuthHeaderConfig`（`security/McpClientAuthHeaderConfig.java`）向 MCP Client 的 `WebClient.Builder` 注入 `ExchangeFilterFunction`，把 `PermissionContextHolder` 中的权限上下文自动透传为 HTTP Header，让 MCP Server 的 `GatewayGovernanceFilter` 能对 `/mcp` 流量做鉴权。

**注入的 Header**（与 MCP Server `application.yml` 对齐）：

| Header | 来源 | 必填 | 说明 |
|---|---|---|---|
| `X-User-Id` | `PermissionContext.userId` | ✅ | 缺失时注入 `anonymous`，保证握手阶段连通 |
| `X-User-Roles` | `PermissionContext.roles` | ❌ | 空值不注入 |
| `X-Session-Id` | `PermissionContext.sessionId` | ❌ | 空值不注入 |

**工作原理**：

```
AgentController.chat()
    │  设置 PermissionContextHolder（ThreadLocal）
    ▼
AgentService.chat()
    │  调用 MCP Client
    ▼
WebClient.Builder（McpClientAuthHeaderConfig 注入的 Filter）
    │  从 ThreadLocal 读取 PermissionContext
    │  注入 X-User-Id / X-User-Roles / X-Session-Id Header
    ▼
POST /mcp（带 Header）
    │
    ▼
MCP Server GatewayGovernanceFilter
    │  校验 X-User-Id（require-auth-header: true）
    │  写入 PermissionContextHolder（MCP Server 侧）
    ▼
@Tool 方法执行 + 审计切面
```

**配置要点**：
- MCP Server 的 `excluded-auth-paths` 已禁用，`/mcp` 流量需要 `X-User-Id` Header
- Agent 侧无需手动处理 Header，由 `McpClientAuthHeaderConfig` 自动注入
- 握手阶段（尚未进入 Controller）注入 `anonymous`，避免 401

**测试覆盖**：`McpClientAuthHeaderConfigTest`（4 个场景）验证 Header 注入逻辑，不依赖 Spring 容器，CI 中稳定运行。
