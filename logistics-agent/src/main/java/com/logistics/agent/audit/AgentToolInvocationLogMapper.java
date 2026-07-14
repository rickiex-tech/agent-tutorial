package com.logistics.agent.audit;

import java.util.List;

public interface AgentToolInvocationLogMapper {

    AgentToolInvocationLogEntity insert(AgentToolInvocationLogEntity entity);

    List<AgentToolInvocationLogEntity> findAll();
}
