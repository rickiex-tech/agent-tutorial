# MCP Tool Naming and Encapsulation Guide

## 1. Layered Naming Convention

### Composite tools (business orchestration)
- Prefix with clear business intent.
- Use `create`, `close`, `dispatch`, `resolve` style verbs.
- Example: `createCustomerServiceTicket`.

### Domain tools (single responsibility)
- One domain action per tool.
- Keep names short and deterministic.
- Examples: `getUser`, `getShipment`, `createTicket`.

### Data tools (query and analytics)
- Distinguish from mutation tools.
- Prefer `query` or `fetch` prefixes.
- Example: `queryOrderMetrics`.

## 2. Encapsulation Rules

1. Composite tools may call domain tools, but domain tools must not call composite tools.
2. Domain tools should represent one API capability and avoid cross-domain orchestration.
3. Data tools should not execute business mutations.
4. Tool names must avoid transport details (`Http`, `Api`, `Client`) and implementation details (`Impl`, `Service`).

## 3. Unified Response Contract

Every tool response must use:
- `code`
- `message`
- `resultType`
- `data`

Result type semantics:
- `success`: business operation completed.
- `business_failure`: business constraint violation, stop without retry.
- `system_failure`: dependency or infrastructure failure, retry under policy.

## 4. Description Standards

Each tool description should include:
1. Business purpose in one sentence.
2. Upstream API mapping when relevant.
3. Failure termination behavior.

## 5. Evolution Checklist

Before adding a new tool:
1. Can an existing composite tool solve it?
2. If no, can a domain tool be reused?
3. Is this query-like (data tool) or mutation-like (domain/composite)?
4. Does the tool follow unified response contract?
5. Is OpenAPI contract updated and covered by CI test?

## 6. Reference in this repo

- Composite: `createCustomerServiceTicket`
- Domain: `getUser`, `getShipment`, `createTicket`
- Data: `queryOrderMetrics`

