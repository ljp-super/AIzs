package com.yupi.yuaiagent.mapper;

import com.yupi.yuaiagent.entity.TokenUsage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TokenUsageMapper {

    private final JdbcTemplate jdbcTemplate;

    public TokenUsageMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createTableIfNotExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS token_usage (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                conversation_id VARCHAR(64),
                model VARCHAR(64),
                prompt_tokens INT,
                completion_tokens INT,
                total_tokens INT,
                cost DOUBLE,
                agent_type VARCHAR(32),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_conversation_id (conversation_id),
                INDEX idx_created_at (created_at),
                INDEX idx_model (model)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
            """;
        jdbcTemplate.execute(sql);
    }

    public void insert(TokenUsage tokenUsage) {
        String sql = """
            INSERT INTO token_usage (conversation_id, model, prompt_tokens, completion_tokens, total_tokens, cost, agent_type, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                tokenUsage.getConversationId(),
                tokenUsage.getModel(),
                tokenUsage.getPromptTokens(),
                tokenUsage.getCompletionTokens(),
                tokenUsage.getTotalTokens(),
                tokenUsage.getCost(),
                tokenUsage.getAgentType(),
                tokenUsage.getCreatedAt() != null ? Timestamp.valueOf(tokenUsage.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
    }

    public List<TokenUsage> findByConversationId(String conversationId) {
        String sql = "SELECT * FROM token_usage WHERE conversation_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, new TokenUsageRowMapper(), conversationId);
    }

    public List<TokenUsage> findRecent(int limit) {
        String sql = "SELECT * FROM token_usage ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, new TokenUsageRowMapper(), limit);
    }

    public long getTotalTokens() {
        String sql = "SELECT SUM(total_tokens) FROM token_usage";
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result != null ? result : 0L;
    }

    public long getTodayTokens() {
        String sql = "SELECT COALESCE(SUM(total_tokens), 0) FROM token_usage WHERE DATE(created_at) = CURDATE()";
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result != null ? result : 0L;
    }

    public double getTotalCost() {
        String sql = "SELECT COALESCE(SUM(cost), 0) FROM token_usage";
        Double result = jdbcTemplate.queryForObject(sql, Double.class);
        return result != null ? result : 0.0;
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM token_usage";
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private static class TokenUsageRowMapper implements RowMapper<TokenUsage> {
        @Override
        public TokenUsage mapRow(ResultSet rs, int rowNum) throws SQLException {
            TokenUsage tokenUsage = new TokenUsage();
            tokenUsage.setId(rs.getLong("id"));
            tokenUsage.setConversationId(rs.getString("conversation_id"));
            tokenUsage.setModel(rs.getString("model"));
            tokenUsage.setPromptTokens(rs.getInt("prompt_tokens"));
            tokenUsage.setCompletionTokens(rs.getInt("completion_tokens"));
            tokenUsage.setTotalTokens(rs.getInt("total_tokens"));
            tokenUsage.setCost(rs.getDouble("cost"));
            tokenUsage.setAgentType(rs.getString("agent_type"));
            tokenUsage.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
            return tokenUsage;
        }
    }
}
