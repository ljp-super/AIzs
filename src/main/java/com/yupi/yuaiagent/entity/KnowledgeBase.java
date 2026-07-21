package com.yupi.yuaiagent.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeBase {

    private Long id;

    private String title;

    private String content;

    private String category;

    private String source;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
