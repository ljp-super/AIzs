package com.yupi.yuaiagent.evaluation;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yupi.yuaiagent.app.LoveApp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RagEvaluationService {

    @Autowired(required = false)
    private LoveApp loveApp;

    @Autowired(required = false)
    private ChatModel chatModel;

    @Value("${ai.evaluation.dataset-path:classpath:rag-evaluation-dataset.json}")
    private String datasetPath;

    /**
     * 运行 RAG 评估
     *
     * @return 评估结果 Map
     */
    public Map<String, Object> runEvaluation() {
        Map<String, Object> result = new HashMap<>();

        if (loveApp == null) {
            log.warn("LoveApp 未注入，无法运行 RAG 评估");
            result.put("error", "LoveApp 未注入，无法运行评估");
            return result;
        }

        // 1. 加载评估数据集
        List<JSONObject> testCases;
        try {
            String json = loadDataset();
            JSONArray array = JSONUtil.parseArray(json);
            testCases = new ArrayList<>();
            for (Object item : array) {
                if (item instanceof JSONObject jsonObject) {
                    testCases.add(jsonObject);
                }
            }
        } catch (Exception e) {
            log.error("加载评估数据集失败", e);
            result.put("error", "加载评估数据集失败: " + e.getMessage());
            return result;
        }

        int totalCases = testCases.size();
        List<Map<String, Object>> details = new ArrayList<>();
        int totalKeywords = 0;
        int hitKeywords = 0;
        long totalResponseTime = 0L;
        List<Double> llmScores = new ArrayList<>();

        // 2. 逐条评估
        for (JSONObject testCase : testCases) {
            Map<String, Object> detail = new HashMap<>();
            String id = testCase.getStr("id");
            String query = testCase.getStr("query");
            String category = testCase.getStr("category");
            List<String> expectedKeywords = parseKeywordList(testCase);

            detail.put("id", id);
            detail.put("query", query);
            detail.put("category", category);
            detail.put("expectedKeywords", expectedKeywords);

            // 调用 RAG 并计时
            String response = null;
            long responseTimeMs = 0L;
            try {
                long start = System.currentTimeMillis();
                response = loveApp.doChatWithRag(query, "eval_session_" + id);
                responseTimeMs = System.currentTimeMillis() - start;
            } catch (Exception e) {
                log.error("用例 {} 调用 RAG 失败", id, e);
                detail.put("error", e.getMessage());
            }

            detail.put("response", response);
            detail.put("responseTimeMs", responseTimeMs);
            totalResponseTime += responseTimeMs;

            // 检查关键词命中
            List<String> hitList = new ArrayList<>();
            if (response != null && expectedKeywords != null) {
                for (String keyword : expectedKeywords) {
                    totalKeywords++;
                    if (response.contains(keyword)) {
                        hitKeywords++;
                        hitList.add(keyword);
                    }
                }
            }
            detail.put("hitKeywords", hitList);

            // LLM-as-Judge 评分
            Double llmScore = null;
            if (chatModel != null && response != null) {
                try {
                    String prompt = "请对以下 RAG 回答质量评分(1-5分)，只返回数字。问题：" + query + "\n回答：" + response;
                    String scoreText = chatModel.call(new Prompt(prompt))
                            .getResult()
                            .getOutput()
                            .getText();
                    llmScore = parseScore(scoreText);
                    if (llmScore != null) {
                        llmScores.add(llmScore);
                    }
                } catch (Exception e) {
                    log.warn("用例 {} LLM 评分失败: {}", id, e.getMessage());
                }
            }
            detail.put("llmScore", llmScore);

            details.add(detail);
        }

        // 3. 汇总结果
        double keywordHitRate = totalKeywords == 0 ? 0 : (hitKeywords * 100.0 / totalKeywords);
        double avgLlmScore = llmScores.isEmpty() ? 0 : llmScores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
        double avgResponseTimeMs = totalCases == 0 ? 0 : (double) totalResponseTime / totalCases;

        result.put("totalCases", totalCases);
        result.put("keywordHitRate", Math.round(keywordHitRate * 100.0) / 100.0);
        result.put("avgLlmScore", Math.round(avgLlmScore * 100.0) / 100.0);
        result.put("avgResponseTimeMs", Math.round(avgResponseTimeMs * 100.0) / 100.0);
        result.put("details", details);

        log.info("RAG 评估完成: 用例数={}, 关键词命中率={}%, 平均LLM评分={}, 平均响应时间={}ms",
                totalCases, Math.round(keywordHitRate * 100.0) / 100.0, avgLlmScore, avgResponseTimeMs);

        return result;
    }

    /**
     * 加载数据集内容，支持 classpath: 前缀和文件路径
     */
    private String loadDataset() throws Exception {
        String path = datasetPath;
        if (path != null && path.startsWith("classpath:")) {
            String resourcePath = path.substring("classpath:".length());
            ClassPathResource resource = new ClassPathResource(resourcePath);
            try (InputStream is = resource.getInputStream()) {
                return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            }
        }
        // 兼容外部文件路径
        Path filePath = Paths.get(path);
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * 解析 expectedKeywords 关键词列表
     */
    private List<String> parseKeywordList(JSONObject testCase) {
        List<String> keywords = new ArrayList<>();
        JSONArray kwArray = testCase.getJSONArray("expectedKeywords");
        if (kwArray != null) {
            for (Object kw : kwArray) {
                keywords.add(String.valueOf(kw));
            }
        }
        return keywords;
    }

    /**
     * 从 LLM 评分文本中解析数字分数（1-5）
     */
    private Double parseScore(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("\\d+(\\.\\d+)?").matcher(text);
        if (matcher.find()) {
            try {
                double v = Double.parseDouble(matcher.group());
                if (v >= 1 && v <= 5) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
