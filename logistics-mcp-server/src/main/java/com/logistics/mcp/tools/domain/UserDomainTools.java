package com.logistics.mcp.tools.domain;

import com.logistics.mcp.common.ToolResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** 用户域领域工具。 */
@Service
public class UserDomainTools {

    /** 查询用户信息。对接 GET /api/v1/users/{userId}。 */
    @Tool(description = "查询用户信息，对接用户域微服务 GET /api/v1/users/{userId}")
    public ToolResult<User> getUser(@ToolParam(description = "用户 ID") long userId) {
        if (userId == MockData.UNAVAILABLE_USER_ID) {
            return ToolResult.systemFailure(50001, "user service timeout");
        }
        User user = MockData.USERS.get(userId);
        if (user == null) {
            return ToolResult.businessFailure(40401, "user not found: " + userId);
        }
        return ToolResult.success(user);
    }
}
