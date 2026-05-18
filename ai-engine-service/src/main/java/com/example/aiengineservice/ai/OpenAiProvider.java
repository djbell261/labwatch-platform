package com.example.aiengineservice.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiProvider implements AiProvider {

    private final String apiKey;
    private final String model;
    private final RestClient restClient;

    public OpenAiProvider(
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${ai.openai.model:gpt-4o-mini}") String model,
            @Value("${ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${app.ai-investigation.timeout-ms:15000}") int timeoutMs
    ) {
        this.apiKey = apiKey;
        this.model = model;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String generateInsight(AiInsightRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured");
        }

        String prompt = buildPrompt(request);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "You are a system monitoring assistant. Analyze telemetry, summarize the likely issue, and recommend actions in 2 to 4 concise sentences."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.2
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = restClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (responseBody == null) {
            throw new IllegalStateException("OpenAI returned an empty response");
        }

        Object choicesObject = responseBody.get("choices");
        if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("OpenAI response did not include choices");
        }

        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            throw new IllegalStateException("OpenAI response choice format was invalid");
        }

        Object messageObject = choiceMap.get("message");
        if (!(messageObject instanceof Map<?, ?> messageMap)) {
            throw new IllegalStateException("OpenAI response message format was invalid");
        }

        Object contentObject = messageMap.get("content");
        if (!(contentObject instanceof String content) || content.isBlank()) {
            throw new IllegalStateException("OpenAI response content was empty");
        }

        return content.trim();
    }

    @Override
    public String generateChatResponse(String userMessage, AiInsightRequest context) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured");
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "You are a system monitoring assistant. Answer operational questions about telemetry, alerts, anomalies, and likely root causes. Keep answers practical and under 5 sentences."),
                        Map.of("role", "user", "content", buildChatPrompt(userMessage, context))
                ),
                "temperature", 0.2
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = restClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (responseBody == null) {
            throw new IllegalStateException("OpenAI returned an empty response");
        }

        Object choicesObject = responseBody.get("choices");
        if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("OpenAI response did not include choices");
        }

        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            throw new IllegalStateException("OpenAI response choice format was invalid");
        }

        Object messageObject = choiceMap.get("message");
        if (!(messageObject instanceof Map<?, ?> messageMap)) {
            throw new IllegalStateException("OpenAI response message format was invalid");
        }

        Object contentObject = messageMap.get("content");
        if (!(contentObject instanceof String content) || content.isBlank()) {
            throw new IllegalStateException("OpenAI response content was empty");
        }

        return content.trim();
    }

    private String buildPrompt(AiInsightRequest request) {
        AiInsightRequest.ActiveAlertsSummary activeAlerts = request.getActiveAlerts();
        AiInsightRequest.AnomaliesSummary anomalies = request.getAnomalies();
        AiInsightRequest.TopProcessSummary topProcess = request.getTopProcess();
        boolean focusedEvent = request.getFocusMetric() != null
                && request.getFocusTimestamp() != null
                && request.getFocusValue() != null;

        return """
                Analyze the following system telemetry and return a short explanation and recommended actions.%s
                CPU Usage: %s
                Memory Usage: %s
                Disk Usage: %s
                Active Alerts: count=%s, types=%s
                Anomalies: count=%s, severity=%s
                Top Process: name=%s, cpu=%s, memory=%s
                Timestamp: %s
                Focus Event Timestamp: %s
                Focus Event Metric: %s
                Focus Event Value: %s
                Focus Event Source: %s
                """.formatted(
                focusedEvent
                        ? " Focus on the selected event and explain the most likely root cause in practical terms, keeping the answer under 5 sentences."
                        : "",
                request.getCpuUsage(),
                request.getMemoryUsage(),
                request.getDiskUsage(),
                activeAlerts != null ? activeAlerts.getCount() : 0,
                activeAlerts != null ? activeAlerts.getTypes() : List.of(),
                anomalies != null ? anomalies.getCount() : 0,
                anomalies != null ? anomalies.getSeverity() : List.of(),
                topProcess != null ? topProcess.getName() : "unknown",
                topProcess != null ? topProcess.getCpu() : null,
                topProcess != null ? topProcess.getMemory() : null,
                request.getTimestamp(),
                request.getFocusTimestamp(),
                request.getFocusMetric(),
                request.getFocusValue(),
                request.getFocusSource()
        );
    }

    private String buildChatPrompt(String userMessage, AiInsightRequest request) {
        AiInsightRequest.ActiveAlertsSummary activeAlerts = request.getActiveAlerts();
        AiInsightRequest.AnomaliesSummary anomalies = request.getAnomalies();
        AiInsightRequest.TopProcessSummary topProcess = request.getTopProcess();

        return """
                User question: %s
                Current telemetry context:
                CPU Usage: %s
                Memory Usage: %s
                Disk Usage: %s
                Active Alerts: count=%s, types=%s
                Anomalies: count=%s, severity=%s
                Top Process: name=%s, cpu=%s, memory=%s
                Timestamp: %s
                Respond with a concise monitoring-focused answer and practical next steps.
                """.formatted(
                userMessage,
                request.getCpuUsage(),
                request.getMemoryUsage(),
                request.getDiskUsage(),
                activeAlerts != null ? activeAlerts.getCount() : 0,
                activeAlerts != null ? activeAlerts.getTypes() : List.of(),
                anomalies != null ? anomalies.getCount() : 0,
                anomalies != null ? anomalies.getSeverity() : List.of(),
                topProcess != null ? topProcess.getName() : "unknown",
                topProcess != null ? topProcess.getCpu() : null,
                topProcess != null ? topProcess.getMemory() : null,
                request.getTimestamp()
        );
    }
}
