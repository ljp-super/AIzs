package com.yupi.yuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 混合检索服务，融合 BM25 关键词检索和向量检索结果
 */
@Component
@Slf4j
public class HybridSearchService {

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Autowired
    private BM25Retriever bm25Retriever;

    @Value("${ai.rag.hybrid.bm25-weight:0.3}")
    private double bm25Weight;

    @Value("${ai.rag.hybrid.vector-weight:0.7}")
    private double vectorWeight;

    @Value("${ai.rag.hybrid.rrf-k:60}")
    private int rrfK;

    @Value("${ai.rag.hybrid.enabled:true}")
    private boolean enabled;

    /**
     * 混合检索：融合 BM25 与向量检索结果（RRF）
     */
    public List<Document> hybridSearch(String query, int topK) {
        if (query == null || query.isEmpty() || topK <= 0) {
            return new ArrayList<>();
        }

        // 决定检索策略：
        // - vectorStore 为 null：只用 BM25 检索
        // - enabled 为 false：只用向量检索
        boolean useVector = vectorStore != null;
        boolean useBm25 = enabled || vectorStore == null;

        List<Document> vectorResults = new ArrayList<>();
        List<Document> bm25Results = new ArrayList<>();

        if (useVector) {
            try {
                List<Document> results = vectorStore.similaritySearch(
                        SearchRequest.builder().query(query).topK(topK * 2).build());
                if (results != null) {
                    vectorResults = results;
                }
            } catch (Exception e) {
                log.warn("Vector search failed: {}", e.getMessage());
            }
        }

        if (useBm25) {
            try {
                List<Document> results = bm25Retriever.search(query, topK * 2);
                if (results != null) {
                    bm25Results = results;
                }
            } catch (Exception e) {
                log.warn("BM25 search failed: {}", e.getMessage());
            }
        }

        // 文档去重（按文档ID）
        Map<String, Document> documentMap = new HashMap<>();
        for (Document doc : vectorResults) {
            documentMap.put(doc.getId(), doc);
        }
        for (Document doc : bm25Results) {
            documentMap.putIfAbsent(doc.getId(), doc);
        }

        // RRF (Reciprocal Rank Fusion) 融合
        Map<String, Double> fusedScores = new HashMap<>();
        for (int i = 0; i < vectorResults.size(); i++) {
            String docId = vectorResults.get(i).getId();
            int rank = i + 1;
            fusedScores.merge(docId, vectorWeight * (1.0 / (rrfK + rank)), Double::sum);
        }
        for (int i = 0; i < bm25Results.size(); i++) {
            String docId = bm25Results.get(i).getId();
            int rank = i + 1;
            fusedScores.merge(docId, bm25Weight * (1.0 / (rrfK + rank)), Double::sum);
        }

        // 按融合分数降序排列，返回 topK 个文档
        List<Document> result = fusedScores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(e -> documentMap.get(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("Hybrid search for query [{}]: vector={}, bm25={}, fused result={}",
                query, vectorResults.size(), bm25Results.size(), result.size());
        return result;
    }
}
