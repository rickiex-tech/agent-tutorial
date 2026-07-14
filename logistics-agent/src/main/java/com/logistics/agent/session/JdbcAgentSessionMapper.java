package com.logistics.agent.session;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "logistics.persistence.in-memory", name = "enabled", havingValue = "false", matchIfMissing = true)
public class JdbcAgentSessionMapper implements AgentSessionMapper {

    private static final RowMapper<AgentSessionEntity> ROW_MAPPER = new RowMapper<>() {
        @Override
        public AgentSessionEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AgentSessionEntity(
                    rs.getLong("id"),
                    rs.getString("session_id"),
                    rs.getLong("user_id"),
                    rs.getString("status"),
                    rs.getString("context"),
                    toLocalDateTime(rs.getTimestamp("create_time")),
                    toLocalDateTime(rs.getTimestamp("update_time"))
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentSessionMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AgentSessionEntity upsert(String sessionId, long userId, String status, String context) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO agent_session (session_id, user_id, status, context, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    user_id = VALUES(user_id),
                    status = VALUES(status),
                    context = VALUES(context),
                    update_time = VALUES(update_time)
                """, sessionId, userId, status, context, Timestamp.valueOf(now), Timestamp.valueOf(now));

        return findBySessionId(sessionId)
                .orElseGet(() -> new AgentSessionEntity(0L, sessionId, userId, status, context, now, now));
    }

    @Override
    public Optional<AgentSessionEntity> findBySessionId(String sessionId) {
        return jdbcTemplate.query(
                        """
                        SELECT id, session_id, user_id, status, context, create_time, update_time
                        FROM agent_session
                        WHERE session_id = ?
                        LIMIT 1
                        """,
                        ROW_MAPPER,
                        sessionId)
                .stream()
                .findFirst();
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
