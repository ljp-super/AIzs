package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.agent.YuManus;
import com.yupi.yuaiagent.app.CompanyPolicyApp;
import com.yupi.yuaiagent.app.LoveApp;
import com.yupi.yuaiagent.app.PdfApp;
import com.yupi.yuaiagent.config.SseEmitterManager;
import com.yupi.yuaiagent.repository.FileRepository;
import com.yupi.yuaiagent.service.ConversationService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private LoveApp loveApp;

    @Resource
    private CompanyPolicyApp companyPolicyApp;

    @Resource
    private PdfApp pdfApp;

    @Resource
    private FileRepository fileRepository;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel chatModel;

    @Resource
    private ChatMemory chatMemory;

    @Resource
    private ConversationService conversationService;

    @Resource
    private SseEmitterManager sseEmitterManager;

    /**
     * 同步调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/love_app/chat/sync")
    public String doChatWithLoveAppSync(String message, String chatId) {
        if (chatId == null || chatId.isEmpty()) {
            chatId = UUID.randomUUID().toString();
        }
        String response = loveApp.doChat(message, chatId);
        conversationService.saveConversation(chatId, "LOVE_APP", message, response, null);
        return response;
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSE(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId);
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithLoveAppServerSentEvent(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse_emitter")
    public SseEmitter doChatWithLoveAppServerSseEmitter(String message, String chatId) {
        if (chatId == null || chatId.isEmpty()) {
            chatId = UUID.randomUUID().toString();
        }
        // 创建带心跳保活和取消支持的 SseEmitter
        SseEmitter sseEmitter = sseEmitterManager.create(chatId, 180000L);
        loveApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        return sseEmitter;
    }

    /**
     * 流式调用 Manus 超级智能体
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/manus/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter doChatWithManus(String message, String chatId) {
        if (chatId == null || chatId.isEmpty()) {
            chatId = UUID.randomUUID().toString();
        }
        YuManus yuManus = new YuManus(allTools, chatModel, chatMemory, chatId);
        return yuManus.runStream(message);
    }

    @GetMapping("/company_policy/chat/sync")
    public String doChatWithCompanyPolicySync(String message, String chatId) {
        if (chatId == null || chatId.isEmpty()) {
            chatId = UUID.randomUUID().toString();
        }
        String response = companyPolicyApp.doChat(message, chatId);
        conversationService.saveConversation(chatId, "COMPANY_POLICY", message, response, null);
        return response;
    }

    @GetMapping(value = "/company_policy/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithCompanyPolicySSE(String message, String chatId) {
        if (chatId == null || chatId.isEmpty()) {
            chatId = UUID.randomUUID().toString();
        }
        return companyPolicyApp.doChatByStream(message, chatId);
    }

    @GetMapping(value = "/pdf/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithPdfSSE(String message, String chatId) {
        if (chatId == null || chatId.isEmpty()) {
            chatId = UUID.randomUUID().toString();
        }
        return pdfApp.doChatByStream(message, chatId);
    }

    @GetMapping("/pdf/chat/sync")
    public String doChatWithPdfSync(String message, String chatId) {
        if (chatId == null || chatId.isEmpty()) {
            chatId = UUID.randomUUID().toString();
        }
        return pdfApp.doChat(message, chatId);
    }

    @PostMapping("/pdf/upload/{chatId}")
    public ResponseEntity<String> uploadPdf(@PathVariable String chatId, @RequestParam("file") MultipartFile file) {
        if (!file.getContentType().equals("application/pdf")) {
            return ResponseEntity.badRequest().body("只能上传PDF文件！");
        }
        
        boolean success = fileRepository.save(chatId, file.getResource());
        if (success) {
            return ResponseEntity.ok("文件上传成功！");
        } else {
            return ResponseEntity.internalServerError().body("文件上传失败！");
        }
    }

    @GetMapping("/pdf/file/{chatId}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadPdf(@PathVariable String chatId) {
        org.springframework.core.io.Resource resource = fileRepository.getFile(chatId);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

    @GetMapping("/pdf/has_file/{chatId}")
    public ResponseEntity<Boolean> hasFile(@PathVariable String chatId) {
        return ResponseEntity.ok(pdfApp.hasFile(chatId));
    }

    /**
     * 取消指定会话的 SSE 流（用户主动取消）
     *
     * @param chatId 会话ID
     * @return 取消结果
     */
    @PostMapping("/sse/cancel")
    public ResponseEntity<Map<String, Object>> cancelSse(String chatId) {
        boolean cancelled = sseEmitterManager.cancel(chatId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", cancelled);
        result.put("chatId", chatId);
        result.put("message", cancelled ? "SSE stream cancelled" : "No active SSE stream for chatId: " + chatId);
        return ResponseEntity.ok(result);
    }

    /**
     * 查询当前活跃的 SSE 连接数
     *
     * @return 活跃连接信息
     */
    @GetMapping("/sse/active")
    public ResponseEntity<Map<String, Object>> getActiveSseConnections() {
        Map<String, Object> result = new HashMap<>();
        result.put("activeCount", sseEmitterManager.getActiveCount());
        result.put("activeChatIds", sseEmitterManager.getActiveChatIds());
        return ResponseEntity.ok(result);
    }
}
