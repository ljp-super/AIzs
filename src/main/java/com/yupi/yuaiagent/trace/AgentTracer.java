package com.yupi.yuaiagent.trace;

import com.yupi.yuaiagent.mapper.AgentTraceMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentTracer {

    private static final Logger log = LoggerFactory.getLogger(AgentTracer.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, AgentTrace> traceStore = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private AgentTraceMapper agentTraceMapper;

    @PostConstruct
    public void init() {
        if (agentTraceMapper != null) {
            try {
                agentTraceMapper.createTableIfNotExists();
                log.info("AgentTrace table initialized for database persistence");
            } catch (Exception e) {
                log.warn("Failed to initialize agent_trace table: {}", e.getMessage());
            }
        }
    }

    public String startTrace(String agentName, String userId, String userQuery) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        AgentTrace trace = new AgentTrace();
        trace.setTraceId(traceId);
        trace.setAgentName(agentName);
        trace.setUserId(userId);
        trace.setUserQuery(userQuery);
        trace.setStartTime(LocalDateTime.now());
        trace.setStatus("RUNNING");
        trace.setSteps(new ArrayList<>());

        traceStore.put(traceId, trace);
        log.info("[AgentTrace] {} started - TraceID: {}, Query: {}", agentName, traceId, userQuery);
        return traceId;
    }

    public void traceStep(String traceId, int stepNumber, String thought, String action, long durationMs) {
        AgentTrace trace = traceStore.get(traceId);
        if (trace != null) {
            StepInfo step = new StepInfo();
            step.setStepNumber(stepNumber);
            step.setThought(thought);
            step.setAction(action);
            step.setDurationMs(durationMs);
            step.setTimestamp(LocalDateTime.now());

            trace.getSteps().add(step);
            log.info("[AgentTrace] {} Step {}: Thought='{}', Action='{}', Duration={}ms",
                    trace.getAgentName(), stepNumber, truncate(thought), truncate(action), durationMs);
        }
    }

    public void traceToolCall(String traceId, String toolName, Map<String, Object> params, String result, long durationMs) {
        AgentTrace trace = traceStore.get(traceId);
        if (trace != null) {
            ToolCallInfo toolCall = new ToolCallInfo();
            toolCall.setToolName(toolName);
            toolCall.setParams(params);
            toolCall.setResult(result);
            toolCall.setDurationMs(durationMs);

            trace.getToolCalls().add(toolCall);
            log.info("[AgentTrace] {} ToolCall: {}({}) = '{}', Duration={}ms",
                    trace.getAgentName(), toolName, params, truncate(result), durationMs);
        }
    }

    public void endTrace(String traceId, String finalResponse, boolean success, String errorMessage) {
        AgentTrace trace = traceStore.get(traceId);
        if (trace != null) {
            trace.setEndTime(LocalDateTime.now());
            trace.setFinalResponse(finalResponse);
            trace.setSuccess(success);
            trace.setErrorMessage(errorMessage);
            trace.setStatus(success ? "COMPLETED" : "FAILED");

            long totalDuration = java.time.Duration.between(trace.getStartTime(), trace.getEndTime()).toMillis();
            trace.setTotalDurationMs(totalDuration);

            log.info("[AgentTrace] {} completed - TraceID: {}, Success: {}, Duration: {}ms, Steps: {}",
                    trace.getAgentName(), traceId, success, totalDuration, trace.getSteps().size());

            if (!success) {
            log.error("[AgentTrace] {} failed - TraceID: {}, Error: {}",
                    trace.getAgentName(), traceId, errorMessage);
        }

        // 持久化到 MySQL，支持跨重启查询和分析
        if (agentTraceMapper != null) {
            try {
                agentTraceMapper.insertTrace(
                        traceId,
                        trace.getAgentName(),
                        trace.getUserId(),
                        trace.getUserQuery(),
                        trace.getStatus(),
                        trace.isSuccess(),
                        trace.getErrorMessage(),
                        trace.getTotalDurationMs(),
                        trace.getStartTime(),
                        trace.getEndTime(),
                        trace.getFinalResponse());
                log.debug("[AgentTrace] Trace persisted to database: {}", traceId);
            } catch (Exception ex) {
                log.warn("[AgentTrace] Failed to persist trace to database: {}", ex.getMessage());
            }
        }
    }
    }

    public AgentTrace getTrace(String traceId) {
        return traceStore.get(traceId);
    }

    public List<AgentTrace> getRecentTraces(int limit) {
        return traceStore.values().stream()
                .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }

    public void clearOldTraces(int hoursToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hoursToKeep);
        traceStore.entrySet().removeIf(entry -> entry.getValue().getStartTime().isBefore(cutoff));
        log.info("[AgentTrace] Cleaned up traces older than {} hours", hoursToKeep);
    }

    private String truncate(String text) {
        if (text == null) return "null";
        return text.length() > 100 ? text.substring(0, 100) + "..." : text;
    }

    @lombok.Data
    public static class AgentTrace {
        private String traceId;
        private String agentName;
        private String userId;
        private String userQuery;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String finalResponse;
        private boolean success;
        private String errorMessage;
        private String status;
        private long totalDurationMs;
        private List<StepInfo> steps = new ArrayList<>();
        private List<ToolCallInfo> toolCalls = new ArrayList<>();
    }

    @lombok.Data
    public static class StepInfo {
        private int stepNumber;
        private String thought;
        private String action;
        private long durationMs;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    public static class ToolCallInfo {
        private String toolName;
        private Map<String, Object> params;
        private String result;
        private long durationMs;
    }
}
