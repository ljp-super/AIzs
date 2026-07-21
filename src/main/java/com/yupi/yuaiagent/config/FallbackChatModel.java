package com.yupi.yuaiagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public class FallbackChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(FallbackChatModel.class);

    private final ChatModel primaryModel;
    private final ChatModel fallbackModel;

    public FallbackChatModel(ChatModel primaryModel, ChatModel fallbackModel) {
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            log.debug("Calling primary model (DeepSeek)");
            return primaryModel.call(prompt);
        } catch (Exception e) {
            log.warn("Primary model (DeepSeek) failed with error: {}", e.getMessage());
            if (fallbackModel != null) {
                log.info("Falling back to secondary model (Ollama)");
                try {
                    return fallbackModel.call(prompt);
                } catch (Exception fallbackException) {
                    log.error("Fallback model (Ollama) also failed: {}", fallbackException.getMessage());
                    throw fallbackException;
                }
            }
            log.error("No fallback model available");
            throw e;
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        try {
            log.debug("Streaming with primary model (DeepSeek)");
            return primaryModel.stream(prompt)
                    .onErrorResume(e -> {
                        log.warn("Primary model (DeepSeek) streaming failed with error: {}", e.getMessage());
                        if (fallbackModel != null) {
                            log.info("Falling back to secondary model (Ollama) for streaming");
                            return fallbackModel.stream(prompt);
                        }
                        log.error("No fallback model available for streaming");
                        return Flux.error(e);
                    });
        } catch (Exception e) {
            log.warn("Primary model (DeepSeek) streaming initialization failed: {}", e.getMessage());
            if (fallbackModel != null) {
                log.info("Falling back to secondary model (Ollama)");
                return fallbackModel.stream(prompt);
            }
            return Flux.error(e);
        }
    }
}
