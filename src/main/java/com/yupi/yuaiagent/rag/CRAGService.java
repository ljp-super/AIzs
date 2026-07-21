package com.yupi.yuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CRAG（Corrective RAG）自我纠错 RAG 服务
 * 在混合检索的基础上引入自我纠错机制：评估初次检索结果质量，
 * 当置信度不足时触发查询重写与二次检索，合并结果后重排序返回，
 * 从而提升检索的准确性与鲁棒性。
 */
@Component
@Slf4j
public class CRAGService {

    @Autowired(required = false)
    private HybridSearchService hybridSearchService;

    @Autowired(required = false)
    private Reranker reranker;

    @Autowired(required = false)
    private QueryRewriter queryRewriter;

    @Value("${ai.rag.crag.enabled:true}")
    private boolean enabled;

    @Value("${ai.rag.crag.confidence-threshold:0.3}")
    private double confidenceThreshold;

    /**
     * CRAG 自我纠错检索
     * 1. 初步检索（取 topK*3 个候选）
     * 2. 评估检索结果质量（基于结果数量和 Reranker 分数）
     * 3. 质量低时：触发查询重写 + 二次检索 + 结果合并
     * 4. 最终重排序返回 topK 个
     */
    public List<Document> cragSearch(String query, int topK) {
        // 依赖不可用时直接返回空列表
        if (hybridSearchService == null) {
            log.warn("CRAG: HybridSearchService is null, returning empty list");
            return new ArrayList<>();
        }
        // 未启用 CRAG 时退化为直接混合检索
        if (!enabled) {
            log.info("CRAG: disabled, fallback to direct hybrid search for query [{}]", query);
            return hybridSearchService.hybridSearch(query, topK);
        }

        // 1. 初步检索：取 topK*3 个候选文档
        List<Document> candidateDocs = hybridSearchService.hybridSearch(query, topK * 3);
        log.info("CRAG: Initial retrieval got {} candidate documents for query [{}]",
                candidateDocs.size(), query);

        // 2. 评估检索结果质量
        boolean lowQuality = false;
        List<Document> evalReranked = null;

        if (candidateDocs.isEmpty() || candidateDocs.size() < 2) {
            // 结果为空或少于 2 个，直接标记为低质量
            lowQuality = true;
            log.warn("CRAG: Low quality - only {} candidate documents", candidateDocs.size());
        } else if (reranker != null) {
            // 有 Reranker：重排序后检查第一个文档的分数（DashScope rerank 结果已按分数降序排列）
            evalReranked = reranker.rerankWithDashScope(query, candidateDocs, topK);
            if (evalReranked.isEmpty()) {
                lowQuality = true;
                log.warn("CRAG: Low quality - rerank returned empty result");
            } else {
                Double topScore = evalReranked.get(0).getScore();
                if (topScore != null && topScore < confidenceThreshold) {
                    lowQuality = true;
                    log.warn("CRAG: Low quality - top document score {} below threshold {}",
                            topScore, confidenceThreshold);
                } else {
                    log.info("CRAG: High confidence retrieval, top score={}", topScore);
                }
            }
        } else {
            // 没有重排序器：结果少于 topK 时标记为低质量
            if (candidateDocs.size() < topK) {
                lowQuality = true;
                log.warn("CRAG: Low quality - only {} candidates less than topK {}",
                        candidateDocs.size(), topK);
            }
        }

        // 质量可信：直接返回评估阶段的重排序结果（有 Reranker）或前 topK（无 Reranker），避免重复重排序
        if (!lowQuality) {
            if (reranker != null) {
                log.info("CRAG: Returning {} reranked documents (high confidence)", evalReranked.size());
                return evalReranked;
            }
            List<Document> topDocs = candidateDocs.stream()
                    .limit(topK)
                    .collect(Collectors.toList());
            log.info("CRAG: Returning top {} documents (high confidence, no rerank)", topDocs.size());
            return topDocs;
        }

        // 3. 纠错流程：查询重写 + 二次检索 + 结果合并
        log.warn("CRAG: Low confidence retrieval, triggering corrective search");

        Map<String, Document> mergedMap = new LinkedHashMap<>();
        for (Document doc : candidateDocs) {
            mergedMap.put(doc.getId(), doc);
        }

        if (queryRewriter != null) {
            try {
                String rewrittenQuery = queryRewriter.doQueryRewrite(query);
                log.info("CRAG: Rewritten query: [{}]", rewrittenQuery);
                List<Document> secondRetrieval = hybridSearchService.hybridSearch(rewrittenQuery, topK * 2);
                log.info("CRAG: Second retrieval got {} documents", secondRetrieval.size());
                for (Document doc : secondRetrieval) {
                    mergedMap.putIfAbsent(doc.getId(), doc);
                }
            } catch (Exception e) {
                log.warn("CRAG: Query rewrite or second retrieval failed: {}", e.getMessage());
            }
        } else {
            log.info("CRAG: QueryRewriter not available, skip query rewrite");
        }

        List<Document> mergedDocs = new ArrayList<>(mergedMap.values());
        log.info("CRAG: Corrective search completed, merged {} documents", mergedDocs.size());

        // 4. 最终重排序：用 Reranker 对合并结果重排序返回 topK 个
        if (reranker != null) {
            List<Document> finalResult = reranker.rerankWithDashScope(query, mergedDocs, topK);
            log.info("CRAG: Final rerank returned {} documents", finalResult.size());
            return finalResult;
        }

        // 5. 没有 Reranker，直接返回前 topK 个
        List<Document> result = mergedDocs.stream()
                .limit(topK)
                .collect(Collectors.toList());
        log.info("CRAG: Returning top {} documents (no rerank)", result.size());
        return result;
    }
}
