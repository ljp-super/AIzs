package com.yupi.yuaiagent.config;

import com.yupi.yuaiagent.service.KnowledgeBaseService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Override
    public void run(String... args) {
        log.info("Initializing application data...");
        knowledgeBaseService.initializeSampleData();
        log.info("Application data initialization complete");
    }
}
