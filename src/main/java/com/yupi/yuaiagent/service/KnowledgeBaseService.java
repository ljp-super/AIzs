package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.entity.KnowledgeBase;
import com.yupi.yuaiagent.mapper.KnowledgeBaseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ChatClient chatClient;
    private boolean databaseAvailable = false;

    @Autowired(required = false)
    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper,
                               ChatClient.Builder chatClientBuilder) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.chatClient = chatClientBuilder.build();
        
        if (knowledgeBaseMapper != null) {
            try {
                knowledgeBaseMapper.createTableIfNotExists();
                databaseAvailable = true;
                log.info("KnowledgeBase table initialized");
            } catch (Exception e) {
                log.warn("Failed to initialize knowledge base table: {}", e.getMessage());
            }
        }
    }

    public void addKnowledge(String title, String content, String category, String source) {
        if (!databaseAvailable || knowledgeBaseMapper == null) {
            log.warn("Database not available, skipping knowledge addition");
            return;
        }
        
        KnowledgeBase kb = new KnowledgeBase();
        kb.setTitle(title);
        kb.setContent(content);
        kb.setCategory(category);
        kb.setSource(source);
        kb.setCreatedAt(LocalDateTime.now());
        knowledgeBaseMapper.insert(kb);
        log.info("Knowledge added: {}", title);
    }

    public List<KnowledgeBase> searchByKeyword(String keyword) {
        if (!databaseAvailable || knowledgeBaseMapper == null) {
            return new ArrayList<>();
        }
        try {
            return knowledgeBaseMapper.searchByKeyword(keyword);
        } catch (Exception e) {
            log.warn("Search failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<KnowledgeBase> searchByCategory(String category) {
        if (!databaseAvailable || knowledgeBaseMapper == null) {
            return new ArrayList<>();
        }
        try {
            return knowledgeBaseMapper.searchByCategory(category);
        } catch (Exception e) {
            log.warn("Search by category failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<KnowledgeBase> getAllKnowledge() {
        if (!databaseAvailable || knowledgeBaseMapper == null) {
            return new ArrayList<>();
        }
        try {
            return knowledgeBaseMapper.getAll();
        } catch (Exception e) {
            log.warn("Get all knowledge failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public int deleteKnowledge(Long id) {
        if (!databaseAvailable || knowledgeBaseMapper == null) {
            return 0;
        }
        try {
            return knowledgeBaseMapper.deleteById(id);
        } catch (Exception e) {
            log.warn("Delete knowledge failed: {}", e.getMessage());
            return 0;
        }
    }

    public long getTotalCount() {
        if (!databaseAvailable || knowledgeBaseMapper == null) {
            return 0;
        }
        try {
            return knowledgeBaseMapper.count();
        } catch (Exception e) {
            log.warn("Count failed: {}", e.getMessage());
            return 0;
        }
    }

    public String queryWithRAG(String userQuery) {
        StringBuilder context = new StringBuilder();
        
        if (databaseAvailable && knowledgeBaseMapper != null) {
            try {
                List<KnowledgeBase> dbResults = knowledgeBaseMapper.searchByKeyword(userQuery);
                if (!dbResults.isEmpty()) {
                    context.append("知识库内容：\n");
                    for (KnowledgeBase kb : dbResults) {
                        context.append("【").append(kb.getTitle()).append("】\n");
                        context.append(kb.getContent()).append("\n\n");
                    }
                }
            } catch (Exception e) {
                log.warn("RAG search failed: {}", e.getMessage());
            }
        }
        
        String systemPrompt;
        if (context.length() > 0) {
            systemPrompt = """
                你是一个知识问答助手。请根据以下提供的知识库内容来回答用户的问题。
                如果知识库中没有相关内容，请直接回答，不需要提及知识库。
                
                知识库内容：
                """ + context;
        } else {
            systemPrompt = "你是一个知识问答助手，请回答用户的问题。";
        }
        
        try {
            ChatResponse response = chatClient.prompt(new Prompt(
                    List.of(
                            new SystemMessage(systemPrompt),
                            new UserMessage(userQuery)
                    )
            )).call().chatResponse();
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("RAG query failed: {}", e.getMessage());
            return "回答失败：" + e.getMessage();
        }
    }

    public void initializeSampleData() {
        if (!databaseAvailable || knowledgeBaseMapper == null) {
            log.info("Database not available, skipping sample data initialization");
            return;
        }
        
        if (knowledgeBaseMapper.count() == 0) {
            log.info("Initializing sample knowledge base data...");
            
            addKnowledge("Spring AI 简介", 
                    "Spring AI 是一个为 Spring 应用程序提供 AI 功能的框架。它支持多种 AI 模型，包括 OpenAI、DeepSeek、Ollama 等。Spring AI 提供了聊天客户端、嵌入模型、向量存储等核心组件，使开发者能够轻松构建 AI 应用。",
                    "技术文档",
                    "官方文档");
            
            addKnowledge("RAG 技术", 
                    "RAG（Retrieval-Augmented Generation）是一种将信息检索与生成式 AI 相结合的技术。它通过在生成回答之前从知识库中检索相关信息，使 AI 能够提供更准确、更可靠的回答。RAG 的主要步骤包括：1. 查询理解；2. 检索相关文档；3. 将文档作为上下文传递给 LLM；4. 生成回答。",
                    "技术文档",
                    "学术论文");
            
            addKnowledge("向量数据库", 
                    "向量数据库是一种专门用于存储和检索向量嵌入的数据库。它使用近似最近邻（ANN）算法来高效地搜索相似向量。常见的向量数据库包括 Pinecone、Milvus、PgVector 等。在 AI 应用中，向量数据库通常用于实现语义搜索和 RAG 功能。",
                    "技术文档",
                    "Wikipedia");
            
            addKnowledge("AI Agent 设计模式", 
                    "AI Agent 是一种能够自主感知环境、做出决策并执行行动的智能系统。常见的设计模式包括：1. ReAct 模式（思考-行动循环）；2. Plan-and-Execute 模式（先规划再执行）；3. Tool Use 模式（使用工具完成任务）。这些模式使 AI 能够处理复杂的多步骤任务。",
                    "技术文档",
                    "研究报告");
            
            addKnowledge("流式响应", 
                    "流式响应是一种将 AI 生成的内容分段返回给客户端的技术。它允许用户在生成完成之前就看到部分内容，提升用户体验。Spring AI 支持 SSE（Server-Sent Events）和 WebSocket 两种流式传输方式。",
                    "技术文档",
                    "官方文档");
            
            log.info("Sample data initialized");
        }
    }
}
