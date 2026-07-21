package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.entity.TokenUsage;
import com.yupi.yuaiagent.trace.TokenUsageTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai/token-usage")
@Slf4j
public class TokenUsageController {

    @Autowired(required = false)
    private TokenUsageTracker tokenUsageTracker;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        if (tokenUsageTracker == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "TokenUsageTracker is not available");
            return ResponseEntity.ok(error);
        }
        return ResponseEntity.ok(tokenUsageTracker.getSummary());
    }

    @GetMapping("/recent")
    public ResponseEntity<List<TokenUsage>> getRecent(@RequestParam(defaultValue = "20") int limit) {
        if (tokenUsageTracker == null) {
            return ResponseEntity.ok(new ArrayList<>());
        }
        return ResponseEntity.ok(tokenUsageTracker.getRecentUsage(limit));
    }

    @GetMapping("/total")
    public ResponseEntity<Long> getTotal() {
        if (tokenUsageTracker == null) {
            return ResponseEntity.ok(0L);
        }
        return ResponseEntity.ok(tokenUsageTracker.getTotalTokens());
    }

    @GetMapping("/today")
    public ResponseEntity<Long> getToday() {
        if (tokenUsageTracker == null) {
            return ResponseEntity.ok(0L);
        }
        return ResponseEntity.ok(tokenUsageTracker.getTodayTokens());
    }
}
