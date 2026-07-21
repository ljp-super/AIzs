package com.yupi.yuaiagent.mapper;

import com.yupi.yuaiagent.entity.KnowledgeBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class KnowledgeBaseMapper {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public KnowledgeBaseMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createTableIfNotExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS knowledge_base (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                title VARCHAR(256) NOT NULL,
                content TEXT NOT NULL,
                category VARCHAR(64),
                source VARCHAR(256),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_category (category),
                FULLTEXT INDEX ft_content (content)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
            """;
        jdbcTemplate.execute(sql);
    }

    public void insert(KnowledgeBase kb) {
        String sql = """
            INSERT INTO knowledge_base (title, content, category, source, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                kb.getTitle(),
                kb.getContent(),
                kb.getCategory(),
                kb.getSource(),
                kb.getCreatedAt() != null ? Timestamp.valueOf(kb.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now()));
    }

    public List<KnowledgeBase> searchByKeyword(String keyword) {
        String sql = "SELECT * FROM knowledge_base WHERE MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE) LIMIT 10";
        return jdbcTemplate.query(sql, new KnowledgeBaseRowMapper(), keyword);
    }

    public List<KnowledgeBase> searchByCategory(String category) {
        String sql = "SELECT * FROM knowledge_base WHERE category = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, new KnowledgeBaseRowMapper(), category);
    }

    public List<KnowledgeBase> getAll() {
        String sql = "SELECT * FROM knowledge_base ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, new KnowledgeBaseRowMapper());
    }

    public int deleteById(Long id) {
        String sql = "DELETE FROM knowledge_base WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM knowledge_base";
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private static class KnowledgeBaseRowMapper implements RowMapper<KnowledgeBase> {
        @Override
        public KnowledgeBase mapRow(ResultSet rs, int rowNum) throws SQLException {
            KnowledgeBase kb = new KnowledgeBase();
            kb.setId(rs.getLong("id"));
            kb.setTitle(rs.getString("title"));
            kb.setContent(rs.getString("content"));
            kb.setCategory(rs.getString("category"));
            kb.setSource(rs.getString("source"));
            kb.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
            kb.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
            return kb;
        }
    }
}
