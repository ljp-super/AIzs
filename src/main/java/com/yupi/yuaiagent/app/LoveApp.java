package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.advisor.InputSanitizerAdvisor;
import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.advisor.ReReadingAdvisor;
import com.yupi.yuaiagent.chatmemory.FileBasedChatMemory;
import com.yupi.yuaiagent.rag.CRAGService;
import com.yupi.yuaiagent.rag.HybridSearchService;
import com.yupi.yuaiagent.rag.LoveAppRagCustomAdvisorFactory;
import com.yupi.yuaiagent.rag.QueryRewriter;
import com.yupi.yuaiagent.rag.Reranker;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class LoveApp {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。" +
            "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
            "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
            "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。" +
            "回答时使用纯文本，不要使用Markdown格式。";

    /**
     * 初始化 ChatClient
     *
     * @param chatModel
     */
    public LoveApp(ChatModel chatModel, @Autowired(required = false) ChatMemory chatMemoryBean) {
        // 优先使用注入的 ChatMemory（基于 Redis 持久化），否则降级到内存
        chatMemory = chatMemoryBean != null ? chatMemoryBean : MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new InputSanitizerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // 自定义日志 Advisor，可按需开启
                        new MyLoggerAdvisor()
//                        // 自定义推理增强 Advisor，可按需开启
//                       ,new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        String[] fullResponse = {""};
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content()
                .doOnNext(chunk -> fullResponse[0] += chunk)
                .doOnComplete(() -> {
                    List<Message> messages = new ArrayList<>();
                    messages.add(new UserMessage(message));
                    messages.add(new AssistantMessage(fullResponse[0]));
                    chatMemory.add(chatId, messages);
                    log.info("Saved conversation to memory: chatId={}, userMsg={}, aiMsg={}", chatId, message, fullResponse[0]);
                })
                .concatWith(Flux.just("[DONE]"));
    }

    record LoveReport(String title, List<String> suggestions) {

    }

    /**
     * AI 恋爱报告功能（实战结构化输出）
     *
     * @param message
     * @param chatId
     * @return
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", loveReport);
        return loveReport;
    }

    // AI 恋爱知识库问答功能

    @Autowired(required = false)
    private VectorStore loveAppVectorStore;

    @Autowired(required = false)
    private Advisor loveAppRagCloudAdvisor;

    @Autowired(required = false)
    private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    @Resource
    private HybridSearchService hybridSearchService;

    @Resource
    private CRAGService cragService;

    @Resource
    private Reranker reranker;

    /**
     * 和 RAG 知识库进行对话
     * 完整 RAG pipeline：查询重写 → 混合检索（向量+BM25）→ 专业重排序（gte-rerank）→ 上下文增强 → AI 生成
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message, String chatId) {
        // 1. 查询重写：将用户口语化查询重写为更适合检索的关键词
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        log.info("RAG pipeline - original: '{}', rewritten: '{}'", message, rewrittenMessage);

        // 2. CRAG 自我纠错检索：混合检索 + 质量评估 + 低质量时查询重写 + 二次检索
        List<Document> retrievedDocs = new ArrayList<>();
        if (cragService != null) {
            retrievedDocs = cragService.cragSearch(rewrittenMessage, 5);
        } else if (hybridSearchService != null) {
            retrievedDocs = hybridSearchService.hybridSearch(rewrittenMessage, 5);
        } else if (loveAppVectorStore != null) {
            // 降级：仅向量检索
            retrievedDocs = loveAppVectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query(rewrittenMessage)
                            .topK(5)
                            .build());
        }
        log.info("RAG pipeline - retrieved {} documents", retrievedDocs.size());

        // 3. 专业重排序：用阿里云 gte-rerank 模型对检索结果精排
        if (retrievedDocs.size() > 3 && reranker != null) {
            retrievedDocs = reranker.rerankWithDashScope(rewrittenMessage, retrievedDocs, 3);
            log.info("RAG pipeline - reranked to {} documents", retrievedDocs.size());
        }

        // 4. 构建增强 prompt：将检索到的上下文注入 prompt
        String context = retrievedDocs.stream()
                .map(this::getDocContent)
                .collect(Collectors.joining("\n\n"));

        String augmentedPrompt = String.format("""
                基于以下参考信息回答用户的问题。如果参考信息中没有相关内容，请根据你的知识回答。

                参考信息：
                %s

                用户问题：%s
                """, context, message);

        // 5. AI 生成回答
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(augmentedPrompt)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("RAG pipeline - generated response length: {}", content != null ? content.length() : 0);
        return content;
    }

    /**
     * 获取文档内容（兼容 Spring AI 不同版本）
     */
    private String getDocContent(Document doc) {
        try {
            return doc.getText();
        } catch (Exception e) {
            return doc.getFormattedContent();
        }
    }

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    /**
     * AI 恋爱报告功能（支持调用工具）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 调用 MCP 服务

    @Autowired(required = false)
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * AI 恋爱报告功能（调用 MCP 服务）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
