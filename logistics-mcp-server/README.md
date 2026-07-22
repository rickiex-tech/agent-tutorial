# logistics-mcp-server — 物流业务能力 MCP 工具服务

物流业务能力的 MCP 工具服务，验证 `build-internal-agent-mcp-architecture` change 的核心架构：
**按业务域封装领域工具 + 对稳定串行链路做业务编排工具 + 统一失败语义**。

> 范围说明：当前工作区无既有 300+ API 与基础设施，本服务用 **mock 数据**模拟既有微服务，
> 聚焦证明「客服工单创建」这一条链路的架构可行性。完整企业级落地见
> `openspec/changes/build-internal-agent-mcp-architecture/`。

## 技术栈

- Spring Boot 4.1.0 / Spring AI 2.0.0 / Java 21 / Maven
- 领域工具与业务编排工具通过 Spring AI `@Tool` 暴露为 **MCP tools**
  （`spring-ai-starter-mcp-server-webmvc`，传输协议：**Streamable HTTP**，默认端口 8080）

## 架构对应

```
智能体 (MCP client)
  │  只看到少量高层工具，而非底层 300+ API
  ▼
业务编排工具层（implements BusinessTool）
  ├─ TrackShipmentTool.trackShipment（查件：只需运单 ID）
  ├─ ExpediteShipmentTool.expediteShipment（催件：用户→运单→状态校验→工单）
  └─ CustomerServiceTicketTool.createCustomerServiceTicket（建工单：用户→运单→工单）
     串行编排，强前置校验、失败即终止
  ▼
领域工具层（implements BusinessTool，按业务域封装，mock 数据）
  ├─ UserDomainTools / ShipmentDomainTools / TicketDomainTools
  ▼
数据工具层（implements DataTool）
  └─ DataDomainTools.getOrderMetrics（运营指标查询）
  ▼
（真实环境）API Gateway → 既有微服务 200+ 业务 API
```

## 关键设计

- **统一响应** [`ToolResult`](src/main/java/com/logistics/mcp/common/ToolResult.java)：`code / message / resultType / data`
- **失败分类** [`ResultType`](src/main/java/com/logistics/mcp/common/ResultType.java)：
  - `BUSINESS_FAILURE`（用户/运单不存在）→ 终止，不重试
  - `SYSTEM_FAILURE`（超时/上游不可用）→ 可重试（`isRetryable()`）
- **串行编排 + 失败即终止**：任意一步未成功，立即返回该步骤失败结果，不执行后续步骤
- **标记接口自动扫描**：业务工具实现 [`BusinessTool`](src/main/java/com/logistics/mcp/tools/BusinessTool.java)、
  数据工具实现 [`DataTool`](src/main/java/com/logistics/mcp/tools/DataTool.java)，Spring 自动注入 `List<BusinessTool>` / `List<DataTool>`，
  无需在 `@Bean` 方法中手动声明。新增工具只需实现接口 + `@Service` + `@Tool`。
- **工具暴露开关** [`ToolExposureProperties`](src/main/java/com/logistics/mcp/ToolExposureProperties.java)：
  通过 `logistics.mcp.tools.business-enabled` / `data-enabled` 控制分组暴露，支持按 profile 独立部署。

## 治理层与可观测性（PoC 近似实现）

- **治理过滤器**：统一认证头校验（`X-User-Id`）、基础限流、权限上下文透传。
- **受控重试**：仅 `SYSTEM_FAILURE` 进入重试策略，`BUSINESS_FAILURE` 立即终止。
- **熔断保护**：基础熔断器避免持续故障放大。
- **审计切面**：所有 `@Tool` 调用写入 `agent_tool_invocation_log`（内存 mapper），记录 `tool_name/tool_layer/result_type/duration_ms`，并做敏感字段脱敏。
- **指标与健康检查**：启用 Actuator 与 Prometheus（`/actuator/health`、`/actuator/prometheus`）。

## MCP Tools 详解

### 工具分层

MCP Server 暴露**两层工具**给智能体（MCP client）：

#### 1️⃣ 业务编排工具（Composite）

| 工具 | 职责 | 签名 | 响应 |
|------|------|------|------|
| **trackShipment** | 查件：查询运单状态与路由，无需用户身份验证 | `(shipmentId: long) -> Shipment` | `ToolResult<Shipment>` |
| **expediteShipment** | 催件：查询用户 → 查询运单 → 校验状态白名单 → 创建催件工单 | `(userId, shipmentId, content) -> Ticket` | `ToolResult<Ticket>` |
| **createCustomerServiceTicket** | 建工单：查询用户 → 查询运单 → 创建客服工单，强前置校验、失败即终止 | `(userId: long, shipmentId: long, content: String) -> Ticket` | `ToolResult<Ticket>` |

**设计点**：
- 智能体只需调用一个高层工具，无需自行拼装底层领域工具或处理中间失败。
- 工具内部实现串行逻辑 + 失败即终止（源码：[TrackShipmentTool](src/main/java/com/logistics/mcp/tools/composite/TrackShipmentTool.java) / [ExpediteShipmentTool](src/main/java/com/logistics/mcp/tools/composite/ExpediteShipmentTool.java) / [CustomerServiceTicketTool](src/main/java/com/logistics/mcp/tools/composite/CustomerServiceTicketTool.java)）。
- 任意一步失败时，立即返回该步骤的失败结果（包括错误码、错误分类、重试标记）。
- 催件工具有状态白名单（`EXPEDITABLE_STATUSES`）：待取件、运输中、派送中、异常件可催件；已签收、已退回不可催件。

#### 2️⃣ 领域工具（Domain）

按业务域封装，每个领域一个工具类，暴露单个职责的原子操作：

| 工具 | 领域 | 职责 | 签名 | 失败行为 |
|------|------|------|------|----------|
| **getUser** | 用户域 | 查询用户信息 | `(userId: long) -> User` | 用户不存在：`BUSINESS_FAILURE`；用户服务超时（ID=1500）：`SYSTEM_FAILURE` |
| **getShipment** | 运单域 | 查询运单信息 | `(shipmentId: long) -> Shipment` | 运单不存在：`BUSINESS_FAILURE` |
| **createTicket** | 客服域 | 创建工单 | `(userId, shipmentId, content, ticketType) -> Ticket` | 工单服务不可用（userId=1002）：`SYSTEM_FAILURE` |

**设计点**：
- 每个工具对接一个既有微服务的 API（示例见工具注释 `对接 GET /api/v1/xxx`）。
- Mock 数据层（[MockData](src/main/java/com/logistics/mcp/tools/domain/MockData.java)）模拟既有数据库和服务行为。
- 真实环境中仅需替换 Mock → 真实 HTTP 调用，工具接口和失败语义保持不变。

#### 3️⃣ 数据工具（Data）

| 工具 | 职责 | 签名 | 响应 |
|------|------|------|------|
| **getOrderMetrics** | 查询运营指标（订单量、履约率等） | `(period: String) -> OrderMetrics` | `ToolResult<OrderMetrics>` |

数据工具实现 `DataTool` 标记接口，受 `data-enabled` 开关控制，可独立部署为数据域 MCP（`data` profile，端口 8082）。

### 响应格式（ToolResult）

所有工具返回统一格式（[源码](src/main/java/com/logistics/mcp/common/ToolResult.java)）：

```java
public record ToolResult<T>(
    int code,                  // 业务码（0=成功, 4xxxx=业务失败, 5xxxx=系统失败）
    String message,            // 错误或成功描述
    ResultType resultType,     // 结果分类：SUCCESS / BUSINESS_FAILURE / SYSTEM_FAILURE
    T data                     // 响应数据（失败时为 null）
)
```

**典型响应示例**：

```json
// ✅ SUCCESS
{
  "code": 0,
  "message": "success",
  "resultType": "SUCCESS",
  "data": { "ticketId": 70001, "userId": 1001, "shipmentId": 9001, ... }
}

// ❌ BUSINESS_FAILURE（用户不存在，不可重试）
{
  "code": 40401,
  "message": "user not found: 1500",
  "resultType": "BUSINESS_FAILURE",
  "data": null,
  "isRetryable": false
}

// ⚠️ SYSTEM_FAILURE（服务超时，可重试）
{
  "code": 50001,
  "message": "user service timeout",
  "resultType": "SYSTEM_FAILURE",
  "data": null,
  "isRetryable": true
}
```

### 失败语义

| resultType | 含义 | 智能体应该怎么做 | 示例 |
|---|---|---|---|
| `SUCCESS` | 操作成功 | 使用返回的 `data`，告诉用户结果 | 工单创建成功，返回工单号 |
| `BUSINESS_FAILURE` | 业务规则冲突，用户信息有问题 | **不重试**，向用户解释失败原因 | 运单不存在、用户不存在 |
| `SYSTEM_FAILURE` | 系统临时故障，可能稍后恢复 | 告诉用户"系统繁忙，请稍后重试" | 服务超时、上游不可用 |

**在智能体中的体现**（见 [AgentService](../logistics-agent/src/main/java/com/logistics/agent/service/AgentService.java)）：
- 工具调用后拿到 `ToolResult`，agent 通过 `isRetryable()` / `resultType` 决定用户可见的回复。
- Agent **无需写任何重试代码** —— 失败语义完全由 prompt 驱动（system prompt 规定三类结果对应的用户回复）。

**系统失败重试策略说明**：当工具返回 `SYSTEM_FAILURE` 时，MCP Server 内部的 `SystemFailureRetryExecutor`（[链接](src/main/java/com/logistics/mcp/common/SystemFailureRetryExecutor.java)）**对用户透明地进行受控重试**（配置项：`logistics.mcp.circuit-breaker.retry-max-attempts`）；若所有重试均失败，工具返回该结果给 Agent，Agent 的 prompt 驱动向最终用户呈现"稍后重试"提示（不再自动调用）。两层重试分别处理内部稳定性和用户可见行为。

### 编排模式：串行 + 失败即终止

[ExpediteShipmentTool](src/main/java/com/logistics/mcp/tools/composite/ExpediteShipmentTool.java) 展示核心模式：

```java
// 步骤 1：查询用户，失败即返回
ToolResult<User> userResult = retryExecutor.execute("get_user",
        () -> userDomainTools.getUser(userId));
if (!userResult.isSuccess()) {
    return userResult.propagateFailure("查询用户信息失败: ");
}

// 步骤 2：查询运单，失败即返回
ToolResult<Shipment> shipmentResult = retryExecutor.execute("get_shipment",
        () -> shipmentDomainTools.getShipment(shipmentId));
if (!shipmentResult.isSuccess()) {
    return shipmentResult.propagateFailure("查询运单信息失败: ");
}

// 步骤 3：校验运单状态（白名单），不通过返回业务失败
ShipmentStatus status = shipmentResult.data().status();
if (!EXPEDITABLE_STATUSES.contains(status)) {
    return ToolResult.businessFailure(40001, "运单状态不支持催件: " + status);
}

// 步骤 4：创建催件工单
ToolResult<Ticket> ticketResult = retryExecutor.execute("create_ticket",
        () -> ticketDomainTools.createTicket(userId, shipmentId, content, TicketType.EXPEDITE));
// ...
```

关键约束：**强前置校验** —— 后续步骤必须等待前序步骤成功，否则立即终止，不执行无效操作。

## 运行

```bash
export JAVA_HOME=/path/to/jdk-21
cd logistics-mcp-server

# 运行测试（正常路径 + 三类异常路径）
mvn test

# 启动 MCP server（将工具暴露给 MCP client）
mvn spring-boot:run
```

按工具层分组启动（模拟独立部署）：

```bash
# 业务域 MCP（domain + composite）
mvn spring-boot:run -Dspring-boot.run.profiles=business

# 数据域 MCP（data）
mvn spring-boot:run -Dspring-boot.run.profiles=data
```

> `business` 默认端口 `8080`，`data` 默认端口 `8082`。

## 测试覆盖

| 场景 | 输入 | 期望结果 |
|------|------|----------|
| 正常路径 | user=1001, shipment=9001 | SUCCESS，返回 ticketId |
| 用户查询失败 | user=1500 | SYSTEM_FAILURE，终止，可重试 |
| 运单不存在 | user=1001, shipment=9999 | BUSINESS_FAILURE，终止，不重试 |
| 创建工单失败 | user=1002, shipment=9002 | SYSTEM_FAILURE，报错 |
| 催件状态白名单 | user=1001, shipment=9004（RETURNED） | BUSINESS_FAILURE，状态不支持催件 |
| 查件成功 | shipment=9001 | SUCCESS，返回运单状态与路由 |
| 查件运单不存在 | shipment=9999 | BUSINESS_FAILURE，终止 |

## OpenAPI 契约与 CI

- 领域工具契约位于 `src/main/resources/openapi/`。
- 契约校验测试位于 `src/test/java/com/logistics/mcp/contracts/OpenApiContractTest.java`。
- 仓库 CI（`.github/workflows/ci.yml`）会执行 `mvn test`，包括契约校验。

## 命名与封装规范

工具命名与封装规范文档见 `../docs/tool-naming-and-encapsulation.md`。
