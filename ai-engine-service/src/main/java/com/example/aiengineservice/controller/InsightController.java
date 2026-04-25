package com.example.aiengineservice.controller;

import com.example.aiengineservice.ai.AiInsightRequest;
import com.example.aiengineservice.service.AiInsightRequestBuilder;
import com.example.aiengineservice.service.AiInsightService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InsightController {

    private final AiInsightRequestBuilder aiInsightRequestBuilder;
    private final AiInsightService aiInsightService;

    public InsightController(
            AiInsightRequestBuilder aiInsightRequestBuilder,
            AiInsightService aiInsightService
    ) {
        this.aiInsightRequestBuilder = aiInsightRequestBuilder;
        this.aiInsightService = aiInsightService;
    }

    @GetMapping("/insight")
    public String getInsight() {
        AiInsightRequest request = aiInsightRequestBuilder.build();
        return aiInsightService.generateInsight(request);
    }
}
