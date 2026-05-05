package com.example.aiengineservice.controller;

import com.example.aiengineservice.ai.AiInsightRequest;
import com.example.aiengineservice.service.AiInsightRequestBuilder;
import com.example.aiengineservice.service.AiInsightService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api", "/api/ai"})
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

    @GetMapping({"/insight", "/ai/insight"})
    public String getInsight(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam(required = false) String machineIdentifier
    ) {
        AiInsightRequest request = aiInsightRequestBuilder.build(authorizationHeader, machineIdentifier);
        return aiInsightService.generateInsight(request);
    }

    @GetMapping({"/insight/event", "/ai/event-insight"})
    public String getEventInsight(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam String timestamp,
            @RequestParam String metric,
            @RequestParam double value,
            @RequestParam(required = false) String machineIdentifier,
            @RequestParam(required = false, defaultValue = "telemetry") String source
    ) {
        AiInsightRequest request = aiInsightRequestBuilder.buildForEvent(
                timestamp,
                metric,
                value,
                source,
                authorizationHeader,
                machineIdentifier
        );
        return aiInsightService.generateInsight(request);
    }
}
