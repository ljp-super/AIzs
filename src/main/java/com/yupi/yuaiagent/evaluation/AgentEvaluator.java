package com.yupi.yuaiagent.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AgentEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AgentEvaluator.class);

    private final ChatModel chatModel;

    @Autowired
    public AgentEvaluator(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public EvaluationResult evaluate(String userQuery, String agentResponse) {
        return evaluate(userQuery, agentResponse, null);
    }

    public EvaluationResult evaluate(String userQuery, String agentResponse, String referenceAnswer) {
        String prompt = """
            Evaluate the following AI response based on the user query:
            
            User Query: %s
            Agent Response: %s
            %s
            
            Rate the response on a scale of 1-5 for each criterion:
            1. Relevance (how relevant is the answer to the question)
            2. Accuracy (how accurate is the information)
            3. Completeness (how complete is the answer)
            4. Clarity (how clear and easy to understand is the answer)
            
            Return format:
            Relevance: X
            Accuracy: X
            Completeness: X
            Clarity: X
            Overall: X
            Comment: brief explanation of your evaluation
            """.formatted(
                userQuery,
                agentResponse,
                referenceAnswer != null ? "Reference Answer: " + referenceAnswer : ""
        );

        try {
            String response = chatModel.call(new Prompt(prompt))
                    .getResult()
                    .getOutput()
                    .getText();

            return parseEvaluation(response);
        } catch (Exception e) {
            log.warn("Evaluation failed: {}", e.getMessage());
            EvaluationResult result = new EvaluationResult();
            result.setError("Evaluation failed: " + e.getMessage());
            return result;
        }
    }

    public List<EvaluationResult> batchEvaluate(List<EvaluationRequest> requests) {
        List<EvaluationResult> results = new ArrayList<>();
        for (EvaluationRequest request : requests) {
            results.add(evaluate(request.getUserQuery(), request.getAgentResponse(), request.getReferenceAnswer()));
        }
        return results;
    }

    public EvaluationSummary summarizeEvaluations(List<EvaluationResult> results) {
        if (results == null || results.isEmpty()) {
            return new EvaluationSummary();
        }

        EvaluationSummary summary = new EvaluationSummary();
        summary.setTotalCount(results.size());
        summary.setSuccessCount((int) results.stream().filter(r -> r.getOverall() >= 3).count());

        double avgRelevance = results.stream().mapToInt(EvaluationResult::getRelevance).average().orElse(0);
        double avgAccuracy = results.stream().mapToInt(EvaluationResult::getAccuracy).average().orElse(0);
        double avgCompleteness = results.stream().mapToInt(EvaluationResult::getCompleteness).average().orElse(0);
        double avgClarity = results.stream().mapToInt(EvaluationResult::getClarity).average().orElse(0);
        double avgOverall = results.stream().mapToInt(EvaluationResult::getOverall).average().orElse(0);

        summary.setAverageRelevance(Math.round(avgRelevance * 100.0) / 100.0);
        summary.setAverageAccuracy(Math.round(avgAccuracy * 100.0) / 100.0);
        summary.setAverageCompleteness(Math.round(avgCompleteness * 100.0) / 100.0);
        summary.setAverageClarity(Math.round(avgClarity * 100.0) / 100.0);
        summary.setAverageOverall(Math.round(avgOverall * 100.0) / 100.0);

        return summary;
    }

    private EvaluationResult parseEvaluation(String response) {
        EvaluationResult result = new EvaluationResult();

        try {
            result.setRelevance(parseScore(response, "Relevance"));
            result.setAccuracy(parseScore(response, "Accuracy"));
            result.setCompleteness(parseScore(response, "Completeness"));
            result.setClarity(parseScore(response, "Clarity"));
            result.setOverall(parseScore(response, "Overall"));
            result.setComment(parseComment(response));
        } catch (Exception e) {
            log.warn("Failed to parse evaluation response: {}", e.getMessage());
            result.setError("Failed to parse evaluation: " + e.getMessage());
        }

        return result;
    }

    private int parseScore(String text, String field) {
        Pattern pattern = Pattern.compile(field + ":\\s*(\\d+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    private String parseComment(String text) {
        Pattern pattern = Pattern.compile("Comment:\\s*(.+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    @lombok.Data
    public static class EvaluationRequest {
        private String userQuery;
        private String agentResponse;
        private String referenceAnswer;
    }

    @lombok.Data
    public static class EvaluationResult {
        private int relevance;
        private int accuracy;
        private int completeness;
        private int clarity;
        private int overall;
        private String comment;
        private String error;
    }

    @lombok.Data
    public static class EvaluationSummary {
        private int totalCount;
        private int successCount;
        private double averageRelevance;
        private double averageAccuracy;
        private double averageCompleteness;
        private double averageClarity;
        private double averageOverall;
    }
}
