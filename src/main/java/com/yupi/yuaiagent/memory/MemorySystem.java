package com.yupi.yuaiagent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MemorySystem {

    private static final Logger log = LoggerFactory.getLogger(MemorySystem.class);

    private VectorStore vectorStore;
    private EmbeddingModel embeddingModel;

    @Autowired(required = false)
    public void setVectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        log.info("MemorySystem: VectorStore injected: {}", vectorStore != null);
    }

    @Autowired(required = false)
    public void setEmbeddingModel(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        log.info("MemorySystem: EmbeddingModel injected: {}", embeddingModel != null);
    }

    public void addMemory(String chatId, String content) {
        if (vectorStore == null) {
            log.debug("VectorStore not available, skipping memory storage");
            return;
        }
        try {
            Document doc = new Document(content);
            doc.getMetadata().put("chatId", chatId);
            doc.getMetadata().put("timestamp", String.valueOf(System.currentTimeMillis()));
            vectorStore.add(List.of(doc));
            log.debug("Memory added for chatId: {}", chatId);
        } catch (Exception e) {
            log.warn("Failed to add memory: {}", e.getMessage());
        }
    }

    public List<String> retrieveMemories(String chatId, String query, int limit) {
        if (vectorStore == null) {
            log.debug("VectorStore not available, returning empty memories");
            return new ArrayList<>();
        }
        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(limit)
                    .build();
            return vectorStore.similaritySearch(searchRequest).stream()
                    .map(this::getDocumentContent)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to retrieve memories: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<String> retrieveMemoriesByChatId(String chatId, int limit) {
        if (vectorStore == null) {
            return new ArrayList<>();
        }
        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query("")
                    .topK(limit)
                    .filterExpression("chatId == '" + chatId + "'")
                    .build();
            return vectorStore.similaritySearch(searchRequest).stream()
                    .map(this::getDocumentContent)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to retrieve memories by chatId: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public void clearMemories(String chatId) {
        if (vectorStore == null) {
            return;
        }
        try {
            vectorStore.delete(List.of(chatId));
            log.debug("Memories cleared for chatId: {}", chatId);
        } catch (Exception e) {
            log.warn("Failed to clear memories: {}", e.getMessage());
        }
    }

    private String getDocumentContent(Document doc) {
        try {
            Method getContentMethod = doc.getClass().getMethod("getContent");
            return (String) getContentMethod.invoke(doc);
        } catch (Exception e) {
            try {
                Method getTextMethod = doc.getClass().getMethod("getText");
                return (String) getTextMethod.invoke(doc);
            } catch (Exception ex) {
                return doc.toString();
            }
        }
    }
}
