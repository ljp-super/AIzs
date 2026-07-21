package com.yupi.yuaiagent.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Conversation {

    private Long id;

    private String chatId;

    private String agentType;

    private String userMessage;

    private String aiResponse;

    private String toolUsed;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
