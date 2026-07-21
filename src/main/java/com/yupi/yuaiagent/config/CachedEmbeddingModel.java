package com.yupi.yuaiagent.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 带缓存的 EmbeddingModel 装饰器
 * 使用 Caffeine 缓存 embedding 结果，避免对相同文本重复调用 embedding API
 * 对于 RAG 场景（重复加载相同文档）可显著降低 API 调用成本
 */
public class CachedEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(CachedEmbeddingModel.class);

    private final EmbeddingModel delegate;
    /** call() 方法缓存（批量 embedding 请求） */
    private final Cache<String, EmbeddingResponse> responseCache;
    /** embed(Document) 方法缓存（单个文档 embedding） */
    private final Cache<String, float[]> embedCache;

    public CachedEmbeddingModel(EmbeddingModel delegate, int maxSize, Duration expireAfterWrite) {
        this.delegate = delegate;
        this.responseCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfterWrite)
                .recordStats()
                .build();
        this.embedCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfterWrite)
                .build();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        String cacheKey = buildCacheKey(request);
        EmbeddingResponse cached = responseCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Embedding response cache hit");
            return cached;
        }
        log.debug("Embedding response cache miss, computing and caching");
        EmbeddingResponse response = delegate.call(request);
        responseCache.put(cacheKey, response);
        return response;
    }

    @Override
    public float[] embed(Document document) {
        String content = getDocumentContent(document);
        float[] cached = embedCache.getIfPresent(content);
        if (cached != null) {
            log.debug("Embed cache hit for document");
            return cached;
        }
        log.debug("Embed cache miss for document, computing and caching");
        float[] result = delegate.embed(document);
        embedCache.put(content, result);
        return result;
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        return "ResponseCache: " + responseCache.stats() + ", EmbedCache: " + embedCache.stats();
    }

    /**
     * 获取文档内容（兼容 Spring AI 不同版本）
     */
    private String getDocumentContent(Document doc) {
        try {
            return doc.getText();
        } catch (Exception e) {
            return doc.getFormattedContent();
        }
    }

    /**
     * 基于输入文本内容生成缓存 key
     */
    private String buildCacheKey(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions();
        if (instructions == null || instructions.isEmpty()) {
            return "empty";
        }
        return instructions.stream()
                .collect(Collectors.joining("|"));
    }
}
