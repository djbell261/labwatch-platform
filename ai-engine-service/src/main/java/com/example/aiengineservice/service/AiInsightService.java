package com.example.aiengineservice.service;

import com.example.aiengineservice.ai.AiInsightRequest;
import com.example.aiengineservice.ai.AiProvider;
import com.example.aiengineservice.ai.MockAiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiInsightService {

    private static final Logger log = LoggerFactory.getLogger(AiInsightService.class);

    private final AiProvider aiProvider;
    private final MockAiProvider mockAiProvider;

    public AiInsightService(AiProvider aiProvider, MockAiProvider mockAiProvider) {
        this.aiProvider = aiProvider;
        this.mockAiProvider = mockAiProvider;
    }

    public String generateInsight(AiInsightRequest request) {
        try {
            return aiProvider.generateInsight(request);
        } catch (Exception exception) {
            log.warn(
                    "AI provider {} failed, falling back to MockAiProvider: {}",
                    aiProvider.getClass().getSimpleName(),
                    exception.getMessage()
            );
            return mockAiProvider.generateInsight(request);
        }
    }
}
