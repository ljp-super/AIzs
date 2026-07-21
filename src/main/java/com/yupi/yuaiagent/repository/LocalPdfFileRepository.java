package com.yupi.yuaiagent.repository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

@Slf4j
@Service
public class LocalPdfFileRepository implements FileRepository {

    private final VectorStore vectorStore;

    public LocalPdfFileRepository(@Autowired(required = false) VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        log.info("LocalPdfFileRepository initialized with vectorStore: {}", vectorStore != null);
    }

    private final Properties chatFiles = new Properties();

    private static final String STORAGE_DIR = "./data/pdf";

    @Override
    public boolean save(String chatId, Resource resource) {
        try {
            File storageDir = new File(STORAGE_DIR);
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            String filename = resource.getFilename();
            File target = new File(storageDir, chatId + "_" + filename);
            Files.copy(resource.getInputStream(), target.toPath());

            chatFiles.put(chatId, target.getName());
            
            if (vectorStore != null) {
                writeToVectorStore(new FileSystemResource(target), chatId);
            } else {
                log.warn("VectorStore is not available, skipping vector store write");
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to save PDF resource.", e);
            return false;
        }
    }

    @Override
    public Resource getFile(String chatId) {
        String filename = chatFiles.getProperty(chatId);
        if (filename == null) {
            return null;
        }
        return new FileSystemResource(new File(STORAGE_DIR, filename));
    }

    @PostConstruct
    private void init() {
        File storageDir = new File(STORAGE_DIR);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        FileSystemResource propertiesResource = new FileSystemResource(STORAGE_DIR + "/chat-pdf.properties");
        if (propertiesResource.exists()) {
            try {
                chatFiles.load(new BufferedReader(new InputStreamReader(propertiesResource.getInputStream(), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                log.error("Failed to load chat-pdf.properties", e);
            }
        }

        if (vectorStore != null) {
            FileSystemResource vectorResource = new FileSystemResource(STORAGE_DIR + "/chat-pdf.json");
            if (vectorResource.exists() && vectorStore instanceof SimpleVectorStore) {
                ((SimpleVectorStore) vectorStore).load(vectorResource);
            }
        }
    }

    @PreDestroy
    private void persistent() {
        try {
            chatFiles.store(new FileWriter(STORAGE_DIR + "/chat-pdf.properties"), LocalDateTime.now().toString());
            if (vectorStore != null && vectorStore instanceof SimpleVectorStore) {
                ((SimpleVectorStore) vectorStore).save(new File(STORAGE_DIR + "/chat-pdf.json"));
            }
        } catch (IOException e) {
            log.error("Failed to persist chat-pdf data", e);
        }
    }

    private void writeToVectorStore(Resource resource, String chatId) {
        PagePdfDocumentReader reader = new PagePdfDocumentReader(
                resource,
                PdfDocumentReaderConfig.builder()
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                        .withPagesPerDocument(1)
                        .build()
        );
        List<Document> documents = reader.read();
        documents.forEach(document -> {
            document.getMetadata().put("chat_id", chatId);
            document.getMetadata().put("file_name", resource.getFilename());
        });
        vectorStore.add(documents);
    }
}
