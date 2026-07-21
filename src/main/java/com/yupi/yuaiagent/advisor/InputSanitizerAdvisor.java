package com.yupi.yuaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * AI 安全防护 Advisor
 * 三道防线：
 * 1. Prompt Injection 检测 - 拦截注入攻击
 * 2. PII 敏感信息脱敏 - 手机号/邮箱/身份证号
 * 3. 敏感词过滤 - 替换危险内容
 * <p>
 * 简历亮点：构建 AI 安全防护层，满足企业合规要求
 */
@Slf4j
public class InputSanitizerAdvisor implements CallAdvisor, StreamAdvisor {

    // Prompt Injection 特征模式
    private static final List<String> INJECTION_PATTERNS = List.of(
            "ignore previous instructions",
            "ignore all previous",
            "disregard the above",
            "you are now",
            "system prompt",
            "reveal your prompt",
            "show me your instructions",
            "忘记之前的指令",
            "忽略以上",
            "你现在是一个",
            "显示你的提示词",
            "无视上述"
    );

    // PII 脱敏正则
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<![0-9])1[3-9]\\d{9}(?![0-9])");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<![0-9])\\d{17}[0-9Xx](?![0-9])");

    // 敏感词列表
    private static final List<String> SENSITIVE_WORDS = List.of(
            "炸弹", "毒品", "枪支", "弹药"
    );

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return -100; // 高优先级，在其他 Advisor 之前执行
    }

    /**
     * 拦截并处理请求
     */
    private ChatClientRequest before(ChatClientRequest request) {
        List<Message> messages = request.prompt().getInstructions();
        if (messages == null || messages.isEmpty()) {
            return request;
        }

        List<Message> sanitizedMessages = new ArrayList<>();
        boolean modified = false;

        for (Message message : messages) {
            if (message instanceof UserMessage) {
                String originalText = message.getText();
                String sanitizedText = sanitize(originalText);

                if (!originalText.equals(sanitizedText)) {
                    modified = true;
                    log.info("InputSanitizer: Input sanitized (original length: {}, sanitized length: {})",
                            originalText.length(), sanitizedText.length());
                }

                // 检测 Prompt Injection
                if (detectInjection(originalText)) {
                    log.warn("InputSanitizer: Potential prompt injection detected!");
                    sanitizedText = "[已过滤：检测到潜在注入攻击] " + sanitizedText;
                    modified = true;
                }

                sanitizedMessages.add(new UserMessage(sanitizedText));
            } else {
                sanitizedMessages.add(message);
            }
        }

        if (modified) {
            // 创建新的 Prompt 替换原始消息
            request = request.mutate()
                    .prompt(org.springframework.ai.chat.prompt.Prompt.builder()
                            .messages(sanitizedMessages)
                            .build())
                    .build();
        }

        return request;
    }

    /**
     * 检测 Prompt Injection
     */
    private boolean detectInjection(String text) {
        if (text == null || text.isEmpty()) return false;
        String lowerText = text.toLowerCase();
        for (String pattern : INJECTION_PATTERNS) {
            if (lowerText.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * PII 脱敏 + 敏感词过滤
     */
    private String sanitize(String text) {
        if (text == null || text.isEmpty()) return text;

        String result = text;

        // 手机号脱敏：138****1234
        result = PHONE_PATTERN.matcher(result).replaceAll(m -> {
            String phone = m.group();
            return phone.substring(0, 3) + "****" + phone.substring(7);
        });

        // 邮箱脱敏：z***@example.com
        result = EMAIL_PATTERN.matcher(result).replaceAll(m -> {
            String email = m.group();
            int atIndex = email.indexOf('@');
            if (atIndex > 1) {
                return email.charAt(0) + "***" + email.substring(atIndex);
            }
            return email;
        });

        // 身份证号脱敏：110***********1234
        result = ID_CARD_PATTERN.matcher(result).replaceAll(m -> {
            String id = m.group();
            return id.substring(0, 3) + "***********" + id.substring(14);
        });

        // 敏感词替换
        for (String word : SENSITIVE_WORDS) {
            if (result.contains(word)) {
                result = result.replace(word, "***");
                log.warn("InputSanitizer: Sensitive word filtered");
            }
        }

        return result;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        chatClientRequest = before(chatClientRequest);
        return chain.nextCall(chatClientRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        chatClientRequest = before(chatClientRequest);
        return chain.nextStream(chatClientRequest);
    }
}
