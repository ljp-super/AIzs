package com.yupi.yuaiagent.config;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 Redis 的 ChatMemoryRepository
 * - 对话消息持久化到 Redis List，重启不丢失
 * - key = chat:memory:{conversationId}
 * - 支持 UserMessage / AssistantMessage / SystemMessage 序列化
 * - Redis 不可用时自动降级（方法返回空/无操作），不影响系统运行
 */
@Repository
@Slf4j
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "chat:memory:";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Override
    public List<String> findConversationIds() {
        if (redisTemplate == null) return Collections.emptyList();
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys == null) return Collections.emptyList();
            return keys.stream()
                    .map(k -> k.substring(KEY_PREFIX.length()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to find conversation ids from Redis: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        if (redisTemplate == null || conversationId == null) return Collections.emptyList();
        try {
            String key = KEY_PREFIX + conversationId;
            List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
            if (jsonList == null || jsonList.isEmpty()) return Collections.emptyList();
            List<Message> messages = new ArrayList<>();
            for (String json : jsonList) {
                Message msg = deserializeMessage(json);
                if (msg != null) messages.add(msg);
            }
            return messages;
        } catch (Exception e) {
            log.warn("Failed to find messages from Redis for conversation {}: {}", conversationId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (redisTemplate == null || conversationId == null) return;
        try {
            String key = KEY_PREFIX + conversationId;
            redisTemplate.delete(key);
            if (messages != null && !messages.isEmpty()) {
                List<String> jsonList = new ArrayList<>();
                for (Message message : messages) {
                    jsonList.add(serializeMessage(message));
                }
                redisTemplate.opsForList().rightPushAll(key, jsonList);
            }
        } catch (Exception e) {
            log.warn("Failed to save messages to Redis for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        if (redisTemplate == null || conversationId == null) return;
        try {
            redisTemplate.delete(KEY_PREFIX + conversationId);
        } catch (Exception e) {
            log.warn("Failed to delete conversation from Redis: {}", e.getMessage());
        }
    }

    /**
     * 序列化 Message 为 JSON
     */
    private String serializeMessage(Message message) {
        JSONObject obj = new JSONObject();
        obj.set("type", message.getMessageType().name());
        obj.set("content", message.getText());
        return obj.toString();
    }

    /**
     * 反序列化 JSON 为 Message
     */
    private Message deserializeMessage(String json) {
        try {
            JSONObject obj = JSONUtil.parseObj(json);
            String type = obj.getStr("type");
            String content = obj.getStr("content");
            if (content == null) content = "";
            switch (type) {
                case "USER":
                    return new UserMessage(content);
                case "ASSISTANT":
                    return new AssistantMessage(content);
                case "SYSTEM":
                    return new SystemMessage(content);
                default:
                    return new UserMessage(content);
            }
        } catch (Exception e) {
            log.warn("Failed to deserialize message: {}", e.getMessage());
            return null;
        }
    }
}
