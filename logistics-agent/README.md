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
       AgentController
            │
        AgentService ── ChatClient(通义 qwen) ──┐
            │                                   │ 自主编排
      └──► MCP Client (Streamable HTTP) ──┘
                       │
        http://localhost:8080
                       │
              logistics-mcp-server（工具：createCustomerServiceTicket 等）
```

- 可编辑架构图：[`logistics-agent-architecture.drawio`](./logistics-agent-architecture.drawio)
- **LLM**：通义千问，走 OpenAI 兼容模式（`https://dashscope.aliyuncs.com/compatible-mode/v1`）。
- **工具来源**：MCP Client 经 Streamable HTTP 发现 server 暴露的工具并注册为 `ToolCallback`。
- **失败语义纯 prompt 驱动**：agent 侧不写任何重试/分支代码，工具返回的 `resultType` 原样回灌给 LLM，
  由 system prompt 规定三类结果的用户可见行为。

| resultType        | 含义           | 用户可见行为                          |
| ----------------- | -------------- | ------------------------------------- |
| `SUCCESS`         | 成功           | 自然语言告知结果（如工单号）          |
| `BUSINESS_FAILURE`| 业务规则失败   | 解释原因、引导核对，**不重试**        |
| `SYSTEM_FAILURE`  | 系统临时故障   | "系统繁忙，请稍后重试"，**不重试**    |

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

3. 用 curl 触达 4 个建工单场景（mock 数据见 server 的 `MockData`）：

   ```bash
   # 1) 成功：用户 1001 + 运单 9001 → 创建工单成功
   curl -s -X POST http://localhost:8081/api/v1/agent/chat \
     -H 'Content-Type: application/json' \
     -d '{"sessionId":"sess-1001","userId":1001,"roles":"agent-user","message":"用户1001的运单9001破损了，帮我创建一个客服工单，内容是包裹外箱破损"}'

   # 2) 业务失败：运单 9999 不存在 → 解释并引导核对，不重试
   curl -s -X POST http://localhost:8081/api/v1/agent/chat \
     -H 'Content-Type: application/json' \
     -d '{"sessionId":"sess-1001","userId":1001,"roles":"agent-user","message":"用户1001的运单9999要投诉，帮我建工单"}'

   # 3) 系统失败（查询用户）：用户 1500 上游不可用 → "系统繁忙，请稍后重试"
   curl -s -X POST http://localhost:8081/api/v1/agent/chat \
     -H 'Content-Type: application/json' \
     -d '{"sessionId":"sess-1500","userId":1500,"roles":"agent-user","message":"用户1500的运单9001有问题，帮我建工单"}'

   # 4) 系统失败（创建工单）：用户 1002 工单服务故障 → "系统繁忙，请稍后重试"
   curl -s -X POST http://localhost:8081/api/v1/agent/chat \
     -H 'Content-Type: application/json' \
     -d '{"sessionId":"sess-1002","userId":1002,"roles":"agent-user","message":"用户1002的运单9002延误，帮我建工单"}'
   ```

预期：场景 1 给出工单号；场景 2 解释运单不存在；场景 3、4 提示"系统繁忙，请稍后重试"。

补充：仓库中的 [`requests.http`](./requests.http) 还提供了第 5 个“纯查询运单状态”的请求样例，适合在 VS Code REST Client 中直接联调。

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
