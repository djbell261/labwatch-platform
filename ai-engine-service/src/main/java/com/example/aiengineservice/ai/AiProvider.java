package com.example.aiengineservice.ai;

public interface AiProvider {

    String generateInsight(AiInsightRequest request);

    String generateChatResponse(String userMessage, AiInsightRequest context);
}
