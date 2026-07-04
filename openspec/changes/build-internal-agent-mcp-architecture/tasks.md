> **范围说明**：当前工作区无既有代码库与基础设施，第 0 组为可在本仓库直接运行验证的**最小可运行模块** `logistics-mcp-server`（Spring Boot 4 / Spring AI 2.0，mock 数据），用于证明「客服工单创建」串行编排 + 失败语义的架构可行性。第 1–7 组为完整企业级落地蓝图，待真实工程仓库中实施。

## 0. 最小可运行模块 logistics-mcp-server（本仓库）

- [x] 0.1 创建 `logistics-mcp-server` 项目结构，实现统一工具响应 `ToolResult` 与失败分类枚举（success / business_failure / system_failure）
- [x] 0.2 实现领域工具（mock 数据）：`get_user`、`get_shipment`、`create_ticket`
- [x] 0.3 实现业务编排工具 `create_customer_service_ticket`：串行 get_user → get_shipment → create_ticket，强前置校验、失败即终止
- [x] 0.4 编写单元测试覆盖正常路径与三类异常路径（用户查询失败 / 运单不存在 / 工单创建失败）
- [x] 0.5 提供 README 与启动方式（mvn test / spring-boot:run），运行验证

## 0A. 最小可运行 Agent 消费侧 logistics-agent（本仓库）

> 验证「云 LLM 经 MCP Client 自主编排工具 + 失败语义纯 prompt 驱动」，对应 design.md 决策 8。独立模块（无 parent），端口 8081。

- [x] 0A.1 创建 `logistics-agent` 独立 Spring Boot 模块（端口 8081），依赖 `spring-ai-starter-model-openai` + MCP Client starter
- [x] 0A.2 配置通义 OpenAI 兼容端点（`https://dashscope.aliyuncs.com/compatible-mode/v1`，`qwen-plus`，key 走 `DASHSCOPE_API_KEY` 环境变量）与 MCP Client 指向 `http://localhost:8080/sse`
- [x] 0A.3 实现 `AgentService`（ChatClient + MCP 工具回调 + 失败语义 system prompt）
- [x] 0A.4 实现 `AgentController`：`POST /api/v1/agent/chat`
- [x] 0A.5 端到端验证 4 个场景（正常 / 运单不存在=business_failure / 用户服务不可用=system_failure / 工单服务宕机=system_failure）并提供 README

## 1. 基础设施与治理层

- [ ] 1.1 接入 API Gateway / Service Mesh，配置统一认证、限流、熔断、可观测性
- [ ] 1.2 定义统一工具响应格式（code、message、resultType、data），封装为共享模块
- [ ] 1.3 创建 `agent_session` 表（含 id、session_id、user_id、status、context、create_time、update_time）及对应 Mapper
- [ ] 1.4 创建 `agent_tool_invocation_log` 表（含 tool_name、tool_layer、request_params、response_summary、result_type、duration_ms 等）及对应 Mapper

## 2. 失败语义与公共能力

- [ ] 2.1 实现失败分类枚举与判定逻辑（business_failure / system_failure）
- [ ] 2.2 实现受控重试策略（仅对 system_failure 重试，业务失败终止）
- [ ] 2.3 实现工具调用审计切面（写入 invocation log，敏感字段脱敏，response_summary 仅存摘要）

## 3. 领域工具层（首批）

- [ ] 3.1 封装用户域领域工具 `get_user`（对接 `GET /api/v1/users/{userId}`）
- [ ] 3.2 封装运单域领域工具 `get_shipment`（对接 `GET /api/v1/shipments/{shipmentId}`）
- [ ] 3.3 封装客服域领域工具 `create_ticket`（对接 `POST /api/v1/tickets`）
- [ ] 3.4 为领域工具建立 OpenAPI 契约校验，纳入 CI

## 4. 业务编排工具层（参考用例）

- [ ] 4.1 实现业务编排工具 `create_customer_service_ticket`，串行编排 get_user → get_shipment → create_ticket
- [ ] 4.2 实现强前置校验与失败即终止逻辑（用户/运单查询失败终止，工单创建失败报错）
- [ ] 4.3 为编排工具补充正常路径与三类异常路径的单元/集成测试

## 5. Agent Runtime / Orchestrator

- [ ] 5.1 实现任务理解与工具选择（优先选择业务编排工具）
- [ ] 5.2 实现会话状态管理（多轮交互上下文持久化到 agent_session）
- [ ] 5.3 实现权限上下文在工具调用链中的传递
- [ ] 5.4 将 MCP 工具注册到 Runtime，按业务域/数据域分组暴露

## 6. MCP 服务拆分与部署

- [ ] 6.1 搭建业务域 MCP 服务（用户/运单/客服域）骨架
- [ ] 6.2 搭建数据域 MCP 服务骨架并接入数据工具层
- [ ] 6.3 配置 MCP 服务的独立部署、扩展与健康检查

## 7. 验证与可观测性

- [ ] 7.1 端到端验证客服工单创建用例（覆盖正常与异常路径）
- [ ] 7.2 接入监控与告警（工具调用量、失败率、耗时分布）
- [ ] 7.3 编写工具命名与封装规范文档，作为后续批量扩展的模板
