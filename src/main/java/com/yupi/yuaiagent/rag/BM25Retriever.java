package com.yupi.yuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 纯 Java BM25 算法检索器实现
 */
@Component
@Slf4j
public class BM25Retriever {

    private static final double K1 = 1.5;

    private static final double B = 0.75;

    private static final String TOKEN_SPLIT_REGEX = "[\\p{IsPunctuation}\\s，。！？、；：“”‘’（）《》【】]+";

    /** 词项 -> 文档列表（倒排索引） */
    private final Map<String, List<Posting>> invertedIndex = new HashMap<>();

    /** 文档ID -> 文档长度 */
    private final Map<String, Integer> docLengths = new HashMap<>();

    /** 文档ID -> 原始文档对象 */
    private final Map<String, Document> documentsMap = new HashMap<>();

    private int avgDocLength = 0;

    private int totalDocs = 0;

    /**
     * 建立倒排索引，计算文档长度、平均文档长度、IDF
     */
    public void index(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            log.info("BM25 index: no documents to index");
            return;
        }

        invertedIndex.clear();
        docLengths.clear();
        documentsMap.clear();

        int totalLength = 0;

        for (Document doc : documents) {
            String docId = doc.getId();
            String content = getDocumentContent(doc);

            documentsMap.put(docId, doc);

            String[] tokens = tokenize(content);
            docLengths.put(docId, tokens.length);
            totalLength += tokens.length;

            // 统计当前文档内每个词项的词频
            Map<String, Integer> termFrequencies = new HashMap<>();
            for (String token : tokens) {
                if (token.isEmpty()) {
                    continue;
                }
                termFrequencies.merge(token, 1, Integer::sum);
            }

            // 构建倒排索引
            for (Map.Entry<String, Integer> entry : termFrequencies.entrySet()) {
                Posting posting = new Posting(docId, entry.getValue());
                invertedIndex.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(posting);
            }
        }

        totalDocs = documents.size();
        avgDocLength = totalDocs == 0 ? 0 : totalLength / totalDocs;

        log.info("BM25 index built: {} documents indexed, avgDocLength = {}", totalDocs, avgDocLength);
    }

    /**
     * 返回按 BM25 分数降序排列的 topK 个文档
     */
    public List<Document> search(String query, int topK) {
        if (query == null || query.isEmpty() || totalDocs == 0 || topK <= 0) {
            return new ArrayList<>();
        }

        String[] queryTokens = tokenize(query);
        if (queryTokens.length == 0) {
            return new ArrayList<>();
        }

        // 累加每个文档的 BM25 分数
        Map<String, Double> scores = new HashMap<>();

        for (String term : queryTokens) {
            if (term.isEmpty()) {
                continue;
            }
            List<Posting> postings = invertedIndex.get(term);
            if (postings == null || postings.isEmpty()) {
                continue;
            }

            int docFreq = postings.size();
            double idf = Math.log((totalDocs - docFreq + 0.5) / (docFreq + 0.5) + 1);

            for (Posting posting : postings) {
                String docId = posting.docId;
                int tf = posting.termFrequency;
                int docLength = docLengths.getOrDefault(docId, 0);

                double norm = avgDocLength == 0 ? 0 : (double) docLength / avgDocLength;
                double denominator = tf + K1 * (1 - B + B * norm);
                double score = idf * (tf * (K1 + 1)) / (denominator == 0 ? 1 : denominator);

                scores.merge(docId, score, Double::sum);
            }
        }

        return scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(e -> documentsMap.get(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 清空索引
     */
    public void clear() {
        invertedIndex.clear();
        docLengths.clear();
        documentsMap.clear();
        avgDocLength = 0;
        totalDocs = 0;
        log.info("BM25 index cleared");
    }

    private String[] tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        return text.split(TOKEN_SPLIT_REGEX);
    }

    private String getDocumentContent(Document doc) {
        try {
            return doc.getText();
        } catch (Exception e) {
            return doc.getFormattedContent();
        }
    }

    /**
     * 倒排表条目
     */
    private static class Posting {

        final String docId;

        final int termFrequency;

        Posting(String docId, int termFrequency) {
            this.docId = docId;
            this.termFrequency = termFrequency;
        }
    }
}
