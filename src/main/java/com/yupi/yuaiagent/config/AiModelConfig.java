package com.yupi.yuaiagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI 模型配置
 * - 主力模型：DeepSeek（deepseek-chat，擅长复杂推理）
 * - 备用模型：DashScope（qwen-turbo，轻量快速，用于简单任务 + failover）
 * - 通过 RoutingChatModel 实现智能路由 + 自动降级
 */
@Configuration
public class AiModelConfig {

    private static final Logger log = LoggerFactory.getLogger(AiModelConfig.class);

    @Bean
    @Primary
    public ChatModel primaryChatModel(
            @Qualifier("deepSeekChatModel") ChatModel deepSeekChatModel,
            @Autowired(required = false) @Qualifier("dashscopeChatModel") ChatModel dashScopeChatModel,
            @Value("${ai.routing.simple-threshold:200}") int simpleThreshold) {

        if (dashScopeChatModel != null) {
            log.info("Creating RoutingChatModel: DeepSeek (primary) + DashScope (secondary), threshold={} chars",
                    simpleThreshold);
            return new RoutingChatModel(deepSeekChatModel, dashScopeChatModel, simpleThreshold);
        }

        log.info("Creating primary ChatModel: DeepSeek only (DashScope not available)");
        return deepSeekChatModel;
    }
}
