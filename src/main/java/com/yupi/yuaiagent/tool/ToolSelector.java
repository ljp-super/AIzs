package com.yupi.yuaiagent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ToolSelector {

    private static final Logger log = LoggerFactory.getLogger(ToolSelector.class);

    private final ChatModel chatModel;

    @Autowired
    public ToolSelector(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public ToolCallback selectTool(String userQuery, ToolCallback[] availableTools) {
        if (availableTools == null || availableTools.length == 0) {
            return null;
        }

        Map<String, Double> toolScores = new HashMap<>();
        String queryLower = userQuery.toLowerCase();

        for (ToolCallback tool : availableTools) {
            ToolInfo toolInfo = getToolInfo(tool);
            String toolName = toolInfo.name.toLowerCase();
            String toolDescription = toolInfo.description.toLowerCase();

            double score = calculateSimilarity(queryLower, toolName, toolDescription);
            toolScores.put(toolName, score);
            log.debug("Tool '{}' score: {}", toolName, score);
        }

        String bestToolName = null;
        double maxScore = 0;
        for (Map.Entry<String, Double> entry : toolScores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                bestToolName = entry.getKey();
            }
        }

        if (bestToolName != null && maxScore > 0.1) {
            for (ToolCallback tool : availableTools) {
                ToolInfo toolInfo = getToolInfo(tool);
                if (toolInfo.name.equalsIgnoreCase(bestToolName)) {
                    log.info("Selected tool: {} with score {}", bestToolName, maxScore);
                    return tool;
                }
            }
        }

        log.debug("No suitable tool found, returning null");
        return null;
    }

    private double calculateSimilarity(String query, String toolName, String toolDescription) {
        double score = 0;

        String[] queryWords = query.split("[\\s,，。！？]+");

        for (String word : queryWords) {
            if (toolName.contains(word) || toolDescription.contains(word)) {
                score += 0.3;
            }
        }

        Set<String> querySet = new HashSet<>(Arrays.asList(queryWords));
        Set<String> descSet = new HashSet<>(Arrays.asList(toolDescription.split("[\\s,，。！？]+")));

        Set<String> intersection = new HashSet<>(querySet);
        intersection.retainAll(descSet);

        score += intersection.size() * 0.2;

        return score;
    }

    public List<ToolCallback> selectToolsByAI(String userQuery, ToolCallback[] availableTools) {
        if (availableTools == null || availableTools.length == 0) {
            return new ArrayList<>();
        }

        StringBuilder toolListBuilder = new StringBuilder();
        for (ToolCallback tool : availableTools) {
            ToolInfo toolInfo = getToolInfo(tool);
            toolListBuilder.append("- ")
                    .append(toolInfo.name)
                    .append(": ")
                    .append(toolInfo.description)
                    .append("\n");
        }

        String prompt = """
            Given the user query: '%s'
            And available tools:
            %s
            Which tools are most relevant? Return only the tool names separated by commas.
            If no tools are needed, return 'none'.
            """.formatted(userQuery, toolListBuilder);

        try {
            String response = chatModel.call(new Prompt(prompt))
                    .getResult()
                    .getOutput()
                    .getText();

            List<ToolCallback> selectedTools = new ArrayList<>();
            if (!response.toLowerCase().contains("none")) {
                Pattern pattern = Pattern.compile("([a-zA-Z0-9_-]+)");
                Matcher matcher = pattern.matcher(response);
                while (matcher.find()) {
                    String toolName = matcher.group(1);
                    for (ToolCallback tool : availableTools) {
                        ToolInfo toolInfo = getToolInfo(tool);
                        if (toolInfo.name.equalsIgnoreCase(toolName)) {
                            selectedTools.add(tool);
                            break;
                        }
                    }
                }
            }

            List<String> toolNames = selectedTools.stream()
                    .map(t -> getToolInfo(t).name)
                    .toList();
            log.info("AI-selected tools: {}", toolNames);
            return selectedTools;
        } catch (Exception e) {
            log.warn("AI tool selection failed, falling back to keyword matching: {}", e.getMessage());
            ToolCallback selected = selectTool(userQuery, availableTools);
            return selected != null ? List.of(selected) : new ArrayList<>();
        }
    }

    private ToolInfo getToolInfo(ToolCallback tool) {
        try {
            Object target = getFieldValue(tool, "target");
            if (target != null) {
                Method[] methods = target.getClass().getDeclaredMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class)) {
                        org.springframework.ai.tool.annotation.Tool toolAnnotation = method.getAnnotation(org.springframework.ai.tool.annotation.Tool.class);
                        String name = method.getName();
                        String description = toolAnnotation.description();
                        return new ToolInfo(name, description);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to get tool info via reflection: {}", e.getMessage());
        }

        String className = tool.getClass().getName();
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        String name = simpleName.replace("Callback", "").replace("Tool", "");
        return new ToolInfo(name, "Perform " + name.toLowerCase() + " operations");
    }

    private Object getFieldValue(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field field = obj.getClass().getSuperclass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private record ToolInfo(String name, String description) {}
}
