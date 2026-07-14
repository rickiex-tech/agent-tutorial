package com.logistics.agent.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@ConditionalOnProperty(prefix = "logistics.persistence.in-memory", name = "enabled", havingValue = "false", matchIfMissing = true)
public class JdbcAgentToolInvocationLogMapper implements AgentToolInvocationLogMapper {

    private static final RowMapper<AgentToolInvocationLogEntity> ROW_MAPPER = new RowMapper<>() {
        @Override
        public AgentToolInvocationLogEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AgentToolInvocationLogEntity(
                    rs.getLong("id"),
                    rs.getString("session_id"),
                    rs.getString("tool_name"),
                    rs.getString("tool_layer"),
                    rs.getString("request_params"),
                    rs.getString("response_summary"),
                    rs.getString("result_type"),
                    rs.getLong("duration_ms"),
                    toLocalDateTime(rs.getTimestamp("create_time")),
                    toLocalDateTime(rs.getTimestamp("update_time"))
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentToolInvocationLogMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AgentToolInvocationLogEntity insert(AgentToolInvocationLogEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                            INSERT INTO agent_tool_invocation_log (
                                session_id,
                                tool_name,
                                tool_layer,
                                request_params,
                                response_summary,
                                result_type,
                                duration_ms,
                                create_time,
                                update_time
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, entity.sessionId());
            ps.setString(2, entity.toolName());
            ps.setString(3, entity.toolLayer());
            ps.setString(4, entity.requestParams());
            ps.setString(5, entity.responseSummary());
            ps.setString(6, entity.resultType());
            ps.setLong(7, entity.durationMs());
            ps.setTimestamp(8, Timestamp.valueOf(now));
            ps.setTimestamp(9, Timestamp.valueOf(now));
            return ps;
        }, keyHolder);

        long id = keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
        return new AgentToolInvocationLogEntity(
                id,
                entity.sessionId(),
                entity.toolName(),
                entity.toolLayer(),
                entity.requestParams(),
                entity.responseSummary(),
                entity.resultType(),
                entity.durationMs(),
                now,
                now);
    }

    @Override
    public List<AgentToolInvocationLogEntity> findAll() {
        return jdbcTemplate.query("""
                SELECT id, session_id, tool_name, tool_layer, request_params, response_summary,
                       result_type, duration_ms, create_time, update_time
                FROM agent_tool_invocation_log
                ORDER BY id DESC
                """, ROW_MAPPER);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
