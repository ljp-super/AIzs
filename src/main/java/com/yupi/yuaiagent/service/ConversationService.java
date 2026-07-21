package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.entity.Conversation;
import com.yupi.yuaiagent.mapper.ConversationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationMapper conversationMapper;
    private boolean databaseAvailable = false;

    @Autowired(required = false)
    public ConversationService(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
        if (conversationMapper != null) {
            try {
                conversationMapper.createTableIfNotExists();
                databaseAvailable = true;
                log.info("Conversation table initialized");
            } catch (Exception e) {
                log.warn("Failed to initialize conversation table: {}", e.getMessage());
            }
        }
    }

    public void saveConversation(String chatId, String agentType, String userMessage, String aiResponse, String toolUsed) {
        if (!databaseAvailable || conversationMapper == null) {
            log.debug("Database not available, skipping conversation save");
            return;
        }
        
        try {
            Conversation conversation = new Conversation();
            conversation.setChatId(chatId);
            conversation.setAgentType(agentType);
            conversation.setUserMessage(userMessage);
            conversation.setAiResponse(aiResponse);
            conversation.setToolUsed(toolUsed);
            conversation.setCreatedAt(LocalDateTime.now());
            conversationMapper.insert(conversation);
            log.debug("Conversation saved: chatId={}, agentType={}", chatId, agentType);
        } catch (Exception e) {
            log.warn("Failed to save conversation: {}", e.getMessage());
        }
    }

    public List<Conversation> getConversationsByChatId(String chatId) {
        if (!databaseAvailable || conversationMapper == null) {
            return new ArrayList<>();
        }
        try {
            return conversationMapper.findByChatId(chatId);
        } catch (Exception e) {
            log.warn("Get conversations failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Conversation> getRecentConversations(int limit) {
        if (!databaseAvailable || conversationMapper == null) {
            return new ArrayList<>();
        }
        try {
            return conversationMapper.findRecent(limit);
        } catch (Exception e) {
            log.warn("Get recent conversations failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public int deleteConversations(String chatId) {
        if (!databaseAvailable || conversationMapper == null) {
            return 0;
        }
        try {
            return conversationMapper.deleteByChatId(chatId);
        } catch (Exception e) {
            log.warn("Delete conversations failed: {}", e.getMessage());
            return 0;
        }
    }

    public long getTotalCount() {
        if (!databaseAvailable || conversationMapper == null) {
            return 0;
        }
        try {
            return conversationMapper.count();
        } catch (Exception e) {
            log.warn("Count conversations failed: {}", e.getMessage());
            return 0;
        }
    }
}
