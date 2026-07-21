package com.yupi.yuaiagent.trace;

import com.yupi.yuaiagent.entity.TokenUsage;
import com.yupi.yuaiagent.mapper.TokenUsageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TokenUsageTracker {

    private final TokenUsageMapper tokenUsageMapper;
    private boolean databaseAvailable = false;

    public TokenUsageTracker(@Autowired(required = false) TokenUsageMapper tokenUsageMapper) {
        this.tokenUsageMapper = tokenUsageMapper;
        if (tokenUsageMapper != null) {
            try {
                tokenUsageMapper.createTableIfNotExists();
                this.databaseAvailable = true;
                log.info("TokenUsageTracker initialized with database persistence");
            } catch (Exception e) {
                this.databaseAvailable = false;
                log.warn("Database not available for TokenUsageTracker: {}", e.getMessage());
            }
        } else {
            this.databaseAvailable = false;
            log.warn("TokenUsageMapper not available, TokenUsageTracker running in memory-only mode");
        }
    }

    public void recordUsage(String conversationId, String model, int promptTokens, int completionTokens, String agentType) {
        if (!databaseAvailable) {
            return;
        }
        try {
            int totalTokens = promptTokens + completionTokens;
            double cost = (promptTokens / 1000.0) * 0.001 + (completionTokens / 1000.0) * 0.002;
            TokenUsage usage = new TokenUsage();
            usage.setConversationId(conversationId);
            usage.setModel(model);
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setTotalTokens(totalTokens);
            usage.setCost(cost);
            usage.setAgentType(agentType);
            usage.setCreatedAt(LocalDateTime.now());
            tokenUsageMapper.insert(usage);
        } catch (Exception e) {
            log.warn("Failed to record token usage: {}", e.getMessage());
        }
    }

    public long getTotalTokens() {
        if (!databaseAvailable) {
            return 0;
        }
        try {
            return tokenUsageMapper.getTotalTokens();
        } catch (Exception e) {
            log.warn("Failed to get total tokens: {}", e.getMessage());
            return 0;
        }
    }

    public long getTodayTokens() {
        if (!databaseAvailable) {
            return 0;
        }
        try {
            return tokenUsageMapper.getTodayTokens();
        } catch (Exception e) {
            log.warn("Failed to get today tokens: {}", e.getMessage());
            return 0;
        }
    }

    public List<TokenUsage> getRecentUsage(int limit) {
        if (!databaseAvailable) {
            return new ArrayList<>();
        }
        try {
            return tokenUsageMapper.findRecent(limit);
        } catch (Exception e) {
            log.warn("Failed to get recent usage: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalTokens", getTotalTokens());
        summary.put("todayTokens", getTodayTokens());
        summary.put("totalRecords", getRecordCount());
        summary.put("totalCost", getTotalCost());
        return summary;
    }

    private long getRecordCount() {
        if (!databaseAvailable) {
            return 0;
        }
        try {
            return tokenUsageMapper.count();
        } catch (Exception e) {
            log.warn("Failed to get record count: {}", e.getMessage());
            return 0;
        }
    }

    private double getTotalCost() {
        if (!databaseAvailable) {
            return 0.0;
        }
        try {
            return tokenUsageMapper.getTotalCost();
        } catch (Exception e) {
            log.warn("Failed to get total cost: {}", e.getMessage());
            return 0.0;
        }
    }
}
