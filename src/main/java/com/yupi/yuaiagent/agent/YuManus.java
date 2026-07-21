package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

/**
 * AI 超级智能体（拥有自主规划能力，可以直接使用）
 * 注意：此类为有状态对象，每次调用需手动创建新实例
 */
public class YuManus extends ToolCallAgent {

    private String chatId;

    public YuManus(ToolCallback[] allTools, ChatModel chatModel, ChatMemory chatMemory, String chatId) {
        super(allTools);
        this.setName("yuManus");
        this.chatId = chatId;
        String SYSTEM_PROMPT = """
                You are YuManus, an all-capable AI assistant.
                IMPORTANT RULES:
                1. ALWAYS provide a DIRECT answer. Never show your thinking process, reasoning, or analysis.
                2. NEVER use Markdown formatting like **bold**, *italic*, # headers, or any symbols.
                3. Just give the final answer concisely.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                Analyze user question and respond appropriately.
                For simple questions, answer directly without explanation.
                For complex tasks, use necessary tools and then give a direct answer.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(10);
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
        this.setChatClient(chatClient);
    }
}
