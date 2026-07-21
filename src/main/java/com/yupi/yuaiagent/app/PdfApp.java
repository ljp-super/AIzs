package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.repository.FileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class PdfApp {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;
    private final FileRepository fileRepository;

    private static final String SYSTEM_PROMPT = """
            你是一个专业的PDF文档问答助手。
            你的职责是根据上传的PDF文档内容回答用户的问题。
            
            回答规则：
            1. 必须基于文档内容回答，不要编造信息。
            2. 如果文档中没有相关内容，明确说明"文档中未找到相关内容"。
            3. 回答要简洁明了，直接给出答案。
            4. 不要使用Markdown格式，使用纯文本。
            """;

    public PdfApp(ChatModel chatModel, ChatMemory chatMemory, 
                  @Autowired(required = false) VectorStore vectorStore,
                  FileRepository fileRepository) {
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
        this.fileRepository = fileRepository;

        log.info("PdfApp initialized with vectorStore: {}", vectorStore != null);

        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    public String doChat(String message, String chatId) {
        ChatResponse chatResponse;
        
        if (vectorStore != null) {
            chatResponse = chatClient
                    .prompt()
                    .user(message)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .advisors(new QuestionAnswerAdvisor(vectorStore))
                    .call()
                    .chatResponse();
        } else {
            chatResponse = chatClient
                    .prompt()
                    .user(message)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .call()
                    .chatResponse();
        }
        
        String content = chatResponse.getResult().getOutput().getText();
        log.info("PDF问答 - 问题: {}, 回答: {}", message, content);
        return content;
    }

    public Flux<String> doChatByStream(String message, String chatId) {
        String[] fullResponse = {""};
        
        Flux<String> baseFlux;
        if (vectorStore != null) {
            baseFlux = chatClient
                    .prompt()
                    .user(message)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .advisors(new QuestionAnswerAdvisor(vectorStore))
                    .stream()
                    .content();
        } else {
            baseFlux = chatClient
                    .prompt()
                    .user(message)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .content();
        }
        
        return baseFlux
                .doOnNext(chunk -> fullResponse[0] += chunk)
                .doOnComplete(() -> {
                    List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
                    messages.add(new org.springframework.ai.chat.messages.UserMessage(message));
                    messages.add(new org.springframework.ai.chat.messages.AssistantMessage(fullResponse[0]));
                    chatMemory.add(chatId, messages);
                    log.info("PdfApp: Saved conversation to memory: chatId={}", chatId);
                })
                .concatWith(Flux.just("[DONE]"));
    }

    public boolean hasFile(String chatId) {
        return fileRepository.getFile(chatId) != null;
    }
}
