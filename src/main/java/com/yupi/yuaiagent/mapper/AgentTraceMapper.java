package com.yupi.yuaiagent.mapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AgentTraceMapper {

    private final JdbcTemplate jdbcTemplate;

    public AgentTraceMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createTableIfNotExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS agent_trace (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                trace_id VARCHAR(32) NOT NULL UNIQUE,
                agent_name VARCHAR(64),
                user_id VARCHAR(64),
                user_query TEXT,
                status VARCHAR(16),
                success BOOLEAN,
                error_message TEXT,
                total_duration_ms BIGINT,
                start_time DATETIME,
                end_time DATETIME,
                final_response TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_trace_id (trace_id),
                INDEX idx_created_at (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
            """;
        jdbcTemplate.execute(sql);
    }

    public void insertTrace(String traceId, String agentName, String userId, String userQuery,
                            String status, boolean success, String errorMessage, long totalDurationMs,
                            LocalDateTime startTime, LocalDateTime endTime, String finalResponse) {
        String sql = """
            INSERT INTO agent_trace (trace_id, agent_name, user_id, user_query, status, success,
                error_message, total_duration_ms, start_time, end_time, final_response)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                traceId,
                agentName,
                userId,
                userQuery,
                status,
                success,
                errorMessage,
                totalDurationMs,
                startTime != null ? Timestamp.valueOf(startTime) : null,
                endTime != null ? Timestamp.valueOf(endTime) : null,
                finalResponse);
    }

    public List<Map<String, Object>> findRecent(int limit) {
        String sql = "SELECT * FROM agent_trace ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, new AgentTraceRowMapper(), limit);
    }

    public Map<String, Object> findByTraceId(String traceId) {
        String sql = "SELECT * FROM agent_trace WHERE trace_id = ?";
        List<Map<String, Object>> list = jdbcTemplate.query(sql, new AgentTraceRowMapper(), traceId);
        return list.isEmpty() ? null : list.get(0);
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM agent_trace";
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private static class AgentTraceRowMapper implements RowMapper<Map<String, Object>> {
        @Override
        public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
            Map<String, Object> map = new HashMap<>();
            map.put("id", rs.getLong("id"));
            map.put("traceId", rs.getString("trace_id"));
            map.put("agentName", rs.getString("agent_name"));
            map.put("userId", rs.getString("user_id"));
            map.put("userQuery", rs.getString("user_query"));
            map.put("status", rs.getString("status"));
            map.put("success", rs.getBoolean("success"));
            map.put("errorMessage", rs.getString("error_message"));
            map.put("totalDurationMs", rs.getLong("total_duration_ms"));
            map.put("startTime", rs.getTimestamp("start_time") != null ? rs.getTimestamp("start_time").toLocalDateTime() : null);
            map.put("endTime", rs.getTimestamp("end_time") != null ? rs.getTimestamp("end_time").toLocalDateTime() : null);
            map.put("finalResponse", rs.getString("final_response"));
            map.put("createdAt", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
            return map;
        }
    }
}
