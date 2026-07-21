package com.yupi.yuaiagent.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.File;
import java.time.Duration;

/**
 * 向量存储配置
 * - 使用 SimpleVectorStore（内存向量存储）
 * - 支持文件持久化：启动时加载、关闭时保存，避免重启丢失数据
 * - 当 PgVector 可用时，可通过取消注释 PgVectorVectorStoreConfig 切换
 */
@Configuration
@Slf4j
public class VectorStoreConfig {

    @Value("${ai.vector-store.persist-path:./data/vector-store.json}")
    private String persistPath;

    private SimpleVectorStore primaryVectorStore;

    @Bean
    @Primary
    public VectorStore vectorStore(@Autowired(required = false) EmbeddingModel embeddingModel) {
        if (embeddingModel != null) {
            // 用 CachedEmbeddingModel 包装，启用 embedding 缓存，避免重复计算
            CachedEmbeddingModel cachedEmbeddingModel = new CachedEmbeddingModel(
                    embeddingModel, 1000, Duration.ofHours(24));
            primaryVectorStore = SimpleVectorStore.builder(cachedEmbeddingModel).build();
            // 启动时从文件加载已有向量数据
            loadFromFile(primaryVectorStore);
            log.info("VectorStore created with CachedEmbeddingModel, persistence at: {}", persistPath);
            return primaryVectorStore;
        } else {
            log.warn("No EmbeddingModel available, returning null");
            return null;
        }
    }

    /**
     * 从文件加载向量数据
     */
    private void loadFromFile(SimpleVectorStore vectorStore) {
        File file = new File(persistPath);
        if (file.exists()) {
            try {
                vectorStore.load(file);
                log.info("Loaded vector store from: {}", persistPath);
            } catch (Exception e) {
                log.warn("Failed to load vector store from file: {}", e.getMessage());
            }
        } else {
            // 确保目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
        }
    }

    /**
     * 保存向量数据到文件
     */
    public void saveToFile() {
        if (primaryVectorStore != null) {
            try {
                File file = new File(persistPath);
                File parentDir = file.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                primaryVectorStore.save(file);
                log.info("VectorStore saved to: {}", persistPath);
            } catch (Exception e) {
                log.warn("Failed to save vector store to file: {}", e.getMessage());
            }
        }
    }

    /**
     * 应用关闭时自动保存向量数据
     */
    @PreDestroy
    public void onDestroy() {
        saveToFile();
    }
}
