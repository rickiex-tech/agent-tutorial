package com.logistics.mcp.audit;

import java.util.List;

public interface AgentToolInvocationLogMapper {

    AgentToolInvocationLog insert(AgentToolInvocationLog log);

    List<AgentToolInvocationLog> findAll();
}
