package com.example.aiengineservice.service;

import com.example.aiengineservice.ai.AiInsightRequest;
import com.example.aiengineservice.ai.AiProvider;
import com.example.aiengineservice.ai.MockAiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final AiProvider aiProvider;
    private final MockAiProvider mockAiProvider;
    private final AiInsightRequestBuilder aiInsightRequestBuilder;

    public ChatService(
            AiProvider aiProvider,
            MockAiProvider mockAiProvider,
            AiInsightRequestBuilder aiInsightRequestBuilder
    ) {
        this.aiProvider = aiProvider;
        this.mockAiProvider = mockAiProvider;
        this.aiInsightRequestBuilder = aiInsightRequestBuilder;
    }

    public String generateChatResponse(String userMessage, String authorizationHeader, String machineIdentifier) {
        AiInsightRequest context = aiInsightRequestBuilder.build(authorizationHeader, machineIdentifier);

        try {
            return aiProvider.generateChatResponse(userMessage, context);
        } catch (Exception exception) {
            log.warn(
                    "AI chat provider {} failed, falling back to MockAiProvider: {}",
                    aiProvider.getClass().getSimpleName(),
                    exception.getMessage()
            );
            return mockAiProvider.generateChatResponse(userMessage, context);
        }
    }
}
