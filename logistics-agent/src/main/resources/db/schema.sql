CREATE TABLE IF NOT EXISTS agent_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    context JSON,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_tool_invocation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    tool_layer VARCHAR(32) NOT NULL,
    request_params JSON,
    response_summary JSON,
    result_type VARCHAR(32) NOT NULL,
    duration_ms BIGINT NOT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
);
