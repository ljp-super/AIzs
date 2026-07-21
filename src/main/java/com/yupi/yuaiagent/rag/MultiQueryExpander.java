package com.yupi.yuaiagent.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MultiQueryExpander {

    private static final Logger log = LoggerFactory.getLogger(MultiQueryExpander.class);

    private final ChatModel chatModel;

    @Autowired
    public MultiQueryExpander(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public List<String> expandQuery(String originalQuery) {
        return expandQuery(originalQuery, 3);
    }

    public List<String> expandQuery(String originalQuery, int count) {
        String prompt = """
            Given the user query: '%s', generate %d different rephrasings 
            to capture different aspects of the question.
            Each query should be concise and focus on a different angle.
            Return only the queries, one per line, without any numbering or extra text.
            """.formatted(originalQuery, count);

        try {
            String response = chatModel.call(new Prompt(prompt))
                    .getResult()
                    .getOutput()
                    .getText();

            List<String> expandedQueries = Arrays.stream(response.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !s.matches("^\\d+\\.?\\s*$"))
                    .limit(count)
                    .collect(Collectors.toList());

            expandedQueries.add(0, originalQuery);

            log.info("Expanded query: {} -> {}", originalQuery, expandedQueries);
            return expandedQueries;
        } catch (Exception e) {
            log.warn("Multi-query expansion failed: {}", e.getMessage());
            return List.of(originalQuery);
        }
    }

    public List<String> expandQueryWithSynonyms(String originalQuery) {
        String prompt = """
            Given the user query: '%s'
            Generate:
            1. 2 synonyms or related terms for key concepts
            2. 2 alternative phrasings
            3. 1 more specific version
            4. 1 more general version
            
            Return only the queries, one per line.
            """.formatted(originalQuery);

        try {
            String response = chatModel.call(new Prompt(prompt))
                    .getResult()
                    .getOutput()
                    .getText();

            List<String> expandedQueries = new ArrayList<>(List.of(originalQuery));
            expandedQueries.addAll(Arrays.stream(response.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList()));

            log.info("Synonym expansion: {} -> {} queries", originalQuery, expandedQueries.size());
            return expandedQueries;
        } catch (Exception e) {
            log.warn("Synonym expansion failed: {}", e.getMessage());
            return List.of(originalQuery);
        }
    }
}
