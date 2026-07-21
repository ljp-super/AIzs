package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.entity.KnowledgeBase;
import com.yupi.yuaiagent.service.KnowledgeBaseService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rag")
public class RagController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @GetMapping("/query")
    public String queryWithRAG(@RequestParam String question) {
        return knowledgeBaseService.queryWithRAG(question);
    }

    @PostMapping("/knowledge")
    public Map<String, Object> addKnowledge(
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(defaultValue = "默认分类") String category,
            @RequestParam(defaultValue = "系统") String source) {
        knowledgeBaseService.addKnowledge(title, content, category, source);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "知识库添加成功");
        return result;
    }

    @GetMapping("/knowledge/list")
    public List<KnowledgeBase> getAllKnowledge() {
        return knowledgeBaseService.getAllKnowledge();
    }

    @GetMapping("/knowledge/search")
    public List<KnowledgeBase> searchKnowledge(@RequestParam String keyword) {
        return knowledgeBaseService.searchByKeyword(keyword);
    }

    @GetMapping("/knowledge/category")
    public List<KnowledgeBase> getByCategory(@RequestParam String category) {
        return knowledgeBaseService.searchByCategory(category);
    }

    @DeleteMapping("/knowledge/{id}")
    public Map<String, Object> deleteKnowledge(@PathVariable Long id) {
        int deleted = knowledgeBaseService.deleteKnowledge(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", deleted > 0);
        result.put("message", deleted > 0 ? "删除成功" : "未找到该记录");
        return result;
    }

    @GetMapping("/knowledge/count")
    public Map<String, Object> getKnowledgeCount() {
        Map<String, Object> result = new HashMap<>();
        result.put("count", knowledgeBaseService.getTotalCount());
        return result;
    }

    @PostMapping("/knowledge/init")
    public Map<String, Object> initializeSampleData() {
        knowledgeBaseService.initializeSampleData();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "示例数据初始化完成");
        return result;
    }
}
