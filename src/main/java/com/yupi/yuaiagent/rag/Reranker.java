package com.yupi.yuaiagent.rag;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 重排序器
 * 支持三种重排序策略：
 * 1. rerank() - 关键词匹配（Jaccard + TF），无 API 调用
 * 2. rerankWithAI() - LLM 排序，用大模型判断相关性
 * 3. rerankWithDashScope() - 阿里云 gte-rerank 专业重排序模型（推荐）
 */
@Component
public class Reranker {

    private static final Logger log = LoggerFactory.getLogger(Reranker.class);

    private final ChatModel chatModel;

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${ai.rag.rerank.model:gte-rerank}")
    private String rerankModel;

    @Value("${ai.rag.rerank.top-k:3}")
    private int defaultTopK;

    @Autowired
    public Reranker(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 关键词匹配重排序（Jaccard 相似度 + TF 词频）
     */
    public List<Document> rerank(String query, List<Document> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }

        List<ScoredDocument> scoredDocs = new ArrayList<>();
        for (Document doc : documents) {
            double score = calculateRelevance(query, getDocumentContent(doc));
            scoredDocs.add(new ScoredDocument(doc, score));
        }

        List<Document> result = scoredDocs.stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(topK)
                .map(sd -> sd.document)
                .collect(Collectors.toList());

        log.info("Reranked {} documents to {} for query: {}", documents.size(), result.size(), query);
        return result;
    }

    /**
     * 使用 LLM 进行重排序
     */
    public List<Document> rerankWithAI(String query, List<Document> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }

        StringBuilder docsBuilder = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            docsBuilder.append(i + 1).append(". ").append(getDocumentContent(documents.get(i))).append("\n\n");
        }

        String prompt = """
            Given the user query: '%s'
            And the following documents (numbered 1-%d):
            %s
            
            Rank the documents by relevance to the query from 1 (most relevant) to %d (least relevant).
            Return only the ranking as a comma-separated list of document numbers, e.g., "1,3,2,4".
            """.formatted(query, documents.size(), docsBuilder, documents.size());

        try {
            String response = chatModel.call(new Prompt(prompt))
                    .getResult()
                    .getOutput()
                    .getText();

            List<Integer> rankings = Arrays.stream(response.split(","))
                    .map(String::trim)
                    .filter(s -> s.matches("\\d+"))
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());

            List<Document> result = new ArrayList<>();
            for (Integer rank : rankings) {
                if (rank > 0 && rank <= documents.size()) {
                    result.add(documents.get(rank - 1));
                }
            }

            if (result.size() > topK) {
                result = result.subList(0, topK);
            }

            log.info("AI reranked {} documents to {} for query: {}", documents.size(), result.size(), query);
            return result;
        } catch (Exception e) {
            log.warn("AI reranking failed, falling back to keyword matching: {}", e.getMessage());
            return rerank(query, documents, topK);
        }
    }

    /**
     * 使用阿里云 gte-rerank 专业重排序模型进行重排序（推荐）
     * 相比关键词匹配和 LLM 排序，专业 rerank 模型在准确率和成本上都有优势
     *
     * @param query     用户查询
     * @param documents 候选文档列表
     * @param topK      返回的文档数量
     * @return 按相关性排序的文档列表
     */
    public List<Document> rerankWithDashScope(String query, List<Document> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty()) {
            log.warn("DashScope API key not configured, falling back to AI reranking");
            return rerankWithAI(query, documents, topK);
        }

        try {
            // 提取文档内容
            List<String> docContents = documents.stream()
                    .map(this::getDocumentContent)
                    .collect(Collectors.toList());

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", rerankModel);

            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("documents", docContents);
            requestBody.put("input", input);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("top_n", topK);
            parameters.put("return_documents", false);
            requestBody.put("parameters", parameters);

            // 调用 DashScope gte-rerank API
            String response = HttpRequest.post("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank")
                    .header("Authorization", "Bearer " + dashscopeApiKey)
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(30000)
                    .execute()
                    .body();

            // 解析响应
            JSONObject responseJson = JSONUtil.parseObj(response);
            JSONObject output = responseJson.getJSONObject("output");
            if (output == null) {
                log.warn("DashScope rerank response has no output, falling back to AI reranking. Response: {}", response);
                return rerankWithAI(query, documents, topK);
            }

            JSONArray results = output.getJSONArray("results");
            if (results == null || results.isEmpty()) {
                log.warn("DashScope rerank returned no results, falling back to AI reranking");
                return rerankWithAI(query, documents, topK);
            }

            // 按 relevance_score 排序并取 topK
            List<Document> result = new ArrayList<>();
            List<JSONObject> sortedResults = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                sortedResults.add(results.getJSONObject(i));
            }
            sortedResults.sort((a, b) -> Double.compare(
                    b.getDouble("relevance_score", 0.0),
                    a.getDouble("relevance_score", 0.0)));

            for (int i = 0; i < Math.min(topK, sortedResults.size()); i++) {
                JSONObject resultObj = sortedResults.get(i);
                int index = resultObj.getInt("index");
                if (index >= 0 && index < documents.size()) {
                    result.add(documents.get(index));
                }
            }

            log.info("DashScope gte-rerank reranked {} documents to {} for query: {}", 
                    documents.size(), result.size(), query);
            return result;
        } catch (Exception e) {
            log.warn("DashScope reranking failed, falling back to AI reranking: {}", e.getMessage());
            return rerankWithAI(query, documents, topK);
        }
    }

    private double calculateRelevance(String query, String content) {
        if (query == null || content == null) {
            return 0;
        }

        Set<String> queryWords = new HashSet<>(Arrays.asList(query.toLowerCase().split("[\\s,，。！？、]+")));
        Set<String> contentWords = new HashSet<>(Arrays.asList(content.toLowerCase().split("[\\s,，。！？、]+")));

        if (queryWords.isEmpty()) {
            return 0;
        }

        Set<String> intersection = new HashSet<>(queryWords);
        intersection.retainAll(contentWords);

        double jaccardSimilarity = (double) intersection.size() / (queryWords.size() + contentWords.size() - intersection.size());

        int termFrequency = 0;
        String contentLower = content.toLowerCase();
        for (String word : queryWords) {
            if (!word.isEmpty()) {
                termFrequency += countOccurrences(contentLower, word);
            }
        }

        double normalizedTf = Math.min((double) termFrequency / 10, 1.0);

        return 0.6 * jaccardSimilarity + 0.4 * normalizedTf;
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    private String getDocumentContent(Document doc) {
        try {
            return doc.getText();
        } catch (Exception e) {
            try {
                Method getContentMethod = doc.getClass().getMethod("getFormattedContent");
                return (String) getContentMethod.invoke(doc);
            } catch (Exception ex) {
                return doc.toString();
            }
        }
    }

    private static class ScoredDocument {
        final Document document;
        final double score;

        ScoredDocument(Document document, double score) {
            this.document = document;
            this.score = score;
        }
    }
}
