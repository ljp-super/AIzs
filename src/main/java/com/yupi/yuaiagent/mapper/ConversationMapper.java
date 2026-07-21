package com.yupi.yuaiagent.mapper;

import com.yupi.yuaiagent.entity.Conversation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ConversationMapper {

    private final JdbcTemplate jdbcTemplate;

    public ConversationMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createTableIfNotExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS conversation (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                chat_id VARCHAR(64) NOT NULL,
                agent_type VARCHAR(32) NOT NULL,
                user_message TEXT,
                ai_response TEXT,
                tool_used VARCHAR(256),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_chat_id (chat_id),
                INDEX idx_created_at (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
            """;
        jdbcTemplate.execute(sql);
    }

    public void insert(Conversation conversation) {
        String sql = """
            INSERT INTO conversation (chat_id, agent_type, user_message, ai_response, tool_used, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                conversation.getChatId(),
                conversation.getAgentType(),
                conversation.getUserMessage(),
                conversation.getAiResponse(),
                conversation.getToolUsed(),
                conversation.getCreatedAt() != null ? Timestamp.valueOf(conversation.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
    }

    public List<Conversation> findByChatId(String chatId) {
        String sql = "SELECT * FROM conversation WHERE chat_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, new ConversationRowMapper(), chatId);
    }

    public List<Conversation> findRecent(int limit) {
        String sql = "SELECT * FROM conversation ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, new ConversationRowMapper(), limit);
    }

    public int deleteByChatId(String chatId) {
        String sql = "DELETE FROM conversation WHERE chat_id = ?";
        return jdbcTemplate.update(sql, chatId);
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM conversation";
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private static class ConversationRowMapper implements RowMapper<Conversation> {
        @Override
        public Conversation mapRow(ResultSet rs, int rowNum) throws SQLException {
            Conversation conversation = new Conversation();
            conversation.setId(rs.getLong("id"));
            conversation.setChatId(rs.getString("chat_id"));
            conversation.setAgentType(rs.getString("agent_type"));
            conversation.setUserMessage(rs.getString("user_message"));
            conversation.setAiResponse(rs.getString("ai_response"));
            conversation.setToolUsed(rs.getString("tool_used"));
            conversation.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
            conversation.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
            return conversation;
        }
    }
}
