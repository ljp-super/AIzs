package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.advisor.InputSanitizerAdvisor;
import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.rag.QueryRewriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class CompanyPolicyApp implements ResourceLoaderAware {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    private final VectorStore companyPolicyVectorStore;

    private final QueryRewriter queryRewriter;

    private ResourcePatternResolver resourcePatternResolver;

    @Value("${spring.ai.dashscope.chat.options.model:qwen-plus}")
    private String modelName;

    private static final String SYSTEM_PROMPT_WITH_RAG = """
            你是公司规章制度智能问答助手。
            你的职责是根据公司规章制度文档回答员工的问题。
            
            回答规则：
            1. 必须基于文档内容回答，不要编造信息。
            2. 如果文档中没有相关内容，明确说明"未找到相关规定"。
            3. 回答要简洁明了，直接给出答案。
            4. 引用具体的条款时，注明条款名称。
            5. 不要使用Markdown格式，使用纯文本。
            """;

    public CompanyPolicyApp(ChatModel chatModel,
                           @Autowired(required = false) @Qualifier("loveAppVectorStore") VectorStore companyPolicyVectorStore,
                           QueryRewriter queryRewriter,
                           @Autowired(required = false) EmbeddingModel embeddingModel,
                           @Autowired(required = false) ChatMemoryRepository chatMemoryRepository) {
        VectorStore effectiveVectorStore = companyPolicyVectorStore;
        
        if (effectiveVectorStore == null && embeddingModel != null) {
            try {
                effectiveVectorStore = SimpleVectorStore.builder(embeddingModel).build();
                initSamplePolicyDocuments(effectiveVectorStore);
                log.info("CompanyPolicyApp: Created SimpleVectorStore with sample policy documents");
            } catch (Exception e) {
                log.warn("Failed to create SimpleVectorStore with embedding model: {}", e.getMessage());
                effectiveVectorStore = null;
            }
        }
        
        this.companyPolicyVectorStore = effectiveVectorStore;
        this.queryRewriter = queryRewriter;
        
        boolean hasVectorStore = effectiveVectorStore != null;
        log.info("CompanyPolicyApp initialized with vectorStore: {}", hasVectorStore);
        
        chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository != null ? chatMemoryRepository : new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        
        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT_WITH_RAG)
                .defaultAdvisors(
                        new InputSanitizerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                );
        
        this.chatClient = builder.build();
        
        if (companyPolicyVectorStore != null) {
            loadPolicyDocuments();
        }
    }
    
    private void initSamplePolicyDocuments(VectorStore vectorStore) {
        List<Document> sampleDocuments = new ArrayList<>();
        
        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("category", "leave");
        metadata1.put("title", "请假管理制度");
        sampleDocuments.add(new Document(
            "请假管理制度：员工请假需提前提交申请，审批通过后方可离岗。病假需提供医院证明，事假每次不超过3天。年度累计事假不得超过15天。",
            metadata1
        ));
        
        Map<String, Object> metadata2 = new HashMap<>();
        metadata2.put("category", "attendance");
        metadata2.put("title", "考勤管理制度");
        sampleDocuments.add(new Document(
            "考勤管理制度：工作时间为周一至周五，上午9:00-12:00，下午13:30-18:00。迟到10分钟内不计，迟到超过1小时按旷工半天处理。每月迟到累计超过3次扣发绩效奖金。",
            metadata2
        ));
        
        Map<String, Object> metadata3 = new HashMap<>();
        metadata3.put("category", "overtime");
        metadata3.put("title", "加班管理制度");
        sampleDocuments.add(new Document(
            "加班管理制度：员工因工作需要加班的，需提前申请并经部门负责人批准。工作日加班按1.5倍计发工资，周末加班按2倍计发，法定节假日按3倍计发。加班时间可申请调休。",
            metadata3
        ));
        
        Map<String, Object> metadata4 = new HashMap<>();
        metadata4.put("category", "vacation");
        metadata4.put("title", "年假制度");
        sampleDocuments.add(new Document(
            "年假制度：员工入职满一年后享有带薪年假。工作满1-3年可享受5天年假，满3-5年享受10天，满5年以上享受15天。年假需在当年休完，特殊情况可结转至次年3月。",
            metadata4
        ));
        
        Map<String, Object> metadata5 = new HashMap<>();
        metadata5.put("category", "dress");
        metadata5.put("title", "着装规范");
        sampleDocuments.add(new Document(
            "着装规范：工作时间需穿着正式商务服装。男士着西装衬衫，女士着职业套装或连衣裙。禁止穿着拖鞋、短裤、背心等休闲服饰进入办公区域。",
            metadata5
        ));
        
        Map<String, Object> metadata6 = new HashMap<>();
        metadata6.put("category", "security");
        metadata6.put("title", "信息安全制度");
        sampleDocuments.add(new Document(
            "信息安全制度：员工需妥善保管公司账号密码，不得泄露给他人。禁止使用公司网络访问非法网站。离职时需归还所有公司设备并注销账号。",
            metadata6
        ));
        
        vectorStore.add(sampleDocuments);
        log.info("Loaded {} sample policy documents", sampleDocuments.size());
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourcePatternResolver = (ResourcePatternResolver) resourceLoader;
    }

    @PostConstruct
    public void init() {
        if (companyPolicyVectorStore != null) {
            loadPolicyDocuments();
        }
    }

    private void loadPolicyDocuments() {
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/company_policy_*.md");
            if (resources.length == 0) {
                log.warn("未找到公司规章制度文档");
                return;
            }
            
            List<Document> allDocuments = new ArrayList<>();
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", filename)
                        .build();
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                allDocuments.addAll(reader.get());
            }
            
            TokenTextSplitter textSplitter = new TokenTextSplitter(200, 100, 10, 5000, true);
            List<Document> splitDocuments = textSplitter.apply(allDocuments);
            
            companyPolicyVectorStore.add(splitDocuments);
            log.info("已加载 {} 个公司规章制度文档，共 {} 个文档块", resources.length, splitDocuments.size());
            
        } catch (IOException e) {
            log.error("加载公司规章制度文档失败", e);
        }
    }

    public String doChat(String message, String chatId) {
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse;
        
        if (companyPolicyVectorStore != null) {
            chatResponse = chatClient
                    .prompt()
                    .user(rewrittenMessage)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .advisors(new QuestionAnswerAdvisor(companyPolicyVectorStore))
                    .call()
                    .chatResponse();
        } else {
            chatResponse = chatClient
                    .prompt()
                    .user(rewrittenMessage)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .call()
                    .chatResponse();
        }
        
        String content = chatResponse.getResult().getOutput().getText();
        log.info("公司规章制度问答 - 问题: {}, 回答: {}", message, content);
        return content;
    }

    public Flux<String> doChatByStream(String message, String chatId) {
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        String[] fullResponse = {""};
        
        Flux<String> baseFlux;
        if (companyPolicyVectorStore != null) {
            baseFlux = chatClient
                    .prompt()
                    .user(rewrittenMessage)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .advisors(new QuestionAnswerAdvisor(companyPolicyVectorStore))
                    .stream()
                    .content();
        } else {
            baseFlux = chatClient
                    .prompt()
                    .user(rewrittenMessage)
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
                    log.info("CompanyPolicyApp: Saved conversation to memory: chatId={}", chatId);
                })
                .concatWith(Flux.just("[DONE]"));
    }
}
