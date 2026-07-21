package com.yupi.yuaiagent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/")
public class HomeController {

    @GetMapping
    public Map<String, Object> home() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "AI Agent 服务运行中");
        result.put("version", "1.0.0");
        result.put("description", "默认使用 DeepSeek，网络异常时自动切换到 Ollama");
        
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("健康检查", "GET /api/health");
        endpoints.put("恋爱大师同步聊天", "GET /api/ai/love_app/chat/sync?message=xxx&chatId=xxx");
        endpoints.put("恋爱大师SSE流式聊天", "GET /api/ai/love_app/chat/sse?message=xxx&chatId=xxx");
        endpoints.put("超级智能体聊天", "GET /api/ai/manus/chat?message=xxx");
        result.put("endpoints", endpoints);
        
        return result;
    }
}
