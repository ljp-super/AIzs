package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.evaluation.RagEvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai/rag-evaluation")
@Slf4j
public class RagEvaluationController {

    @Autowired(required = false)
    private RagEvaluationService evaluationService;

    /**
     * 运行 RAG 评估
     *
     * @return 评估结果
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runEvaluation() {
        if (evaluationService == null) {
            log.warn("RagEvaluationService 未注入，RAG 评估不可用");
            Map<String, Object> error = new HashMap<>();
            error.put("error", "RAG 评估服务不可用");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
        }
        Map<String, Object> result = evaluationService.runEvaluation();
        return ResponseEntity.ok(result);
    }
}
