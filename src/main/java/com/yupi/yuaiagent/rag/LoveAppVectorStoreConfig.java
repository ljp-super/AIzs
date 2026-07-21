package com.yupi.yuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@Slf4j
public class LoveAppVectorStoreConfig {

    @jakarta.annotation.Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @jakarta.annotation.Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @jakarta.annotation.Resource
    private BM25Retriever bm25Retriever;

    @Bean
    @ConditionalOnBean(EmbeddingModel.class)
    VectorStore loveAppVectorStore(EmbeddingModel embeddingModel) {
        if (embeddingModel == null) {
            log.warn("EmbeddingModel is null, loveAppVectorStore will not be created");
            return null;
        }
        try {
            SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
            List<Document> documentList = loveAppDocumentLoader.loadMarkdowns();
            simpleVectorStore.add(documentList);
            // 同时索引到 BM25 检索器，支持混合检索
            bm25Retriever.index(documentList);
            log.info("loveAppVectorStore created successfully with {} documents (vector + BM25 indexed)", documentList.size());
            return simpleVectorStore;
        } catch (Exception e) {
            log.warn("Failed to create loveAppVectorStore: {}", e.getMessage());
            return null;
        }
    }
}
