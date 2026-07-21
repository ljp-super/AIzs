package com.yupi.yuaiagent.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TokenUsage {
    private Long id;
    private String conversationId;
    private String model;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private double cost;
    private String agentType;
    private LocalDateTime createdAt;
}
