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
                                "You are an intelligent operations assistant. Use short, calm, beginner-friendly language. Avoid dense monitoring-report wording and avoid raw process names when a plain explanation is available."),
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
                                "You are an intelligent operations assistant. Answer in short, calm, beginner-friendly sections with practical next steps."),
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
                Analyze the following system telemetry and return exactly four lightweight sections:
                WHAT'S HAPPENING
                WHY THIS HAPPENS
                IS IT SERIOUS?
                WHAT TO DO NEXT

                Keep each section to 1 short sentence except WHAT TO DO NEXT, which should use 2 to 4 bullets.
                Prefer plain-English process meaning over raw process names.
                Distinguish normal developer workload from abnormal machine behavior.
                Suppress numeric detail unless severity, confidence, or trend suggests a warning.
                %s
                CPU Usage: %s
                Memory Usage: %s
                Disk Usage: %s
                Machine Identifier: %s
                Active Alerts: count=%s, types=%s
                Anomalies: count=%s, severity=%s
                Recent Metric Trend: %s
                Top Process: name=%s, cpu=%s, memory=%s
                Top Process Interpretation: %s
                Top CPU Process: name=%s, cpu=%s, memory=%s
                Top CPU Process Interpretation: %s
                Top Memory Process: name=%s, cpu=%s, memory=%s
                Top Memory Process Interpretation: %s
                Correlation Timeline: %s
                Incident Group Key: %s
                Incident Status: %s
                Suspected Contributor: %s
                Affected Metrics: %s
                Confidence: %s (%s%%)
                Machine Baseline: %s
                Historical Pattern Notes: %s
                Timestamp: %s
                Focus Event Timestamp: %s
                Focus Event Metric: %s
                Focus Event Value: %s
                Focus Event Source: %s
                """.formatted(
                focusedEvent
                        ? "Focus on the selected event and keep the wording calm and actionable."
                        : "Summarize the most important issue, likely contributor, seriousness, and next steps.",
                request.getCpuUsage(),
                request.getMemoryUsage(),
                request.getDiskUsage(),
                request.getMachineIdentifier(),
                activeAlerts != null ? activeAlerts.getCount() : 0,
                activeAlerts != null ? activeAlerts.getTypes() : List.of(),
                anomalies != null ? anomalies.getCount() : 0,
                anomalies != null ? anomalies.getSeverity() : List.of(),
                request.getRecentMetricTrend(),
                topProcess != null ? topProcess.getName() : "unknown",
                topProcess != null ? topProcess.getCpu() : null,
                topProcess != null ? topProcess.getMemory() : null,
                formatProcessInsight(topProcess),
                request.getTopCpuProcess() != null ? request.getTopCpuProcess().getName() : "unknown",
                request.getTopCpuProcess() != null ? request.getTopCpuProcess().getCpu() : null,
                request.getTopCpuProcess() != null ? request.getTopCpuProcess().getMemory() : null,
                formatProcessInsight(request.getTopCpuProcess()),
                request.getTopMemoryProcess() != null ? request.getTopMemoryProcess().getName() : "unknown",
                request.getTopMemoryProcess() != null ? request.getTopMemoryProcess().getCpu() : null,
                request.getTopMemoryProcess() != null ? request.getTopMemoryProcess().getMemory() : null,
                formatProcessInsight(request.getTopMemoryProcess()),
                request.getCorrelationTimeline() != null ? request.getCorrelationTimeline() : List.of(),
                request.getIncidentGroupKey(),
                request.getIncidentStatus(),
                request.getSuspectedContributor(),
                request.getAffectedMetrics(),
                request.getConfidenceLevel(),
                request.getConfidenceScore(),
                request.getBaselineSummary(),
                request.getHistoricalPatternNotes(),
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
                Machine Identifier: %s
                Active Alerts: count=%s, types=%s
                Anomalies: count=%s, severity=%s
                Recent Metric Trend: %s
                Top Process: name=%s, cpu=%s, memory=%s
                Top Process Interpretation: %s
                Top CPU Process: name=%s, cpu=%s, memory=%s
                Top CPU Process Interpretation: %s
                Top Memory Process: name=%s, cpu=%s, memory=%s
                Top Memory Process Interpretation: %s
                Correlation Timeline: %s
                Incident Group Key: %s
                Incident Status: %s
                Suspected Contributor: %s
                Affected Metrics: %s
                Confidence: %s (%s%%)
                Machine Baseline: %s
                Historical Pattern Notes: %s
                Timestamp: %s
                Respond using exactly four lightweight sections:
                WHAT'S HAPPENING
                WHY THIS HAPPENS
                IS IT SERIOUS?
                WHAT TO DO NEXT

                Keep it short, conversational, and beginner-friendly. Use bullets only in WHAT TO DO NEXT. Prefer plain-English process meaning over raw process names, and clearly say whether this looks like normal developer workload or abnormal machine behavior.
                """.formatted(
                userMessage,
                request.getCpuUsage(),
                request.getMemoryUsage(),
                request.getDiskUsage(),
                request.getMachineIdentifier(),
                activeAlerts != null ? activeAlerts.getCount() : 0,
                activeAlerts != null ? activeAlerts.getTypes() : List.of(),
                anomalies != null ? anomalies.getCount() : 0,
                anomalies != null ? anomalies.getSeverity() : List.of(),
                request.getRecentMetricTrend(),
                topProcess != null ? topProcess.getName() : "unknown",
                topProcess != null ? topProcess.getCpu() : null,
                topProcess != null ? topProcess.getMemory() : null,
                formatProcessInsight(topProcess),
                request.getTopCpuProcess() != null ? request.getTopCpuProcess().getName() : "unknown",
                request.getTopCpuProcess() != null ? request.getTopCpuProcess().getCpu() : null,
                request.getTopCpuProcess() != null ? request.getTopCpuProcess().getMemory() : null,
                formatProcessInsight(request.getTopCpuProcess()),
                request.getTopMemoryProcess() != null ? request.getTopMemoryProcess().getName() : "unknown",
                request.getTopMemoryProcess() != null ? request.getTopMemoryProcess().getCpu() : null,
                request.getTopMemoryProcess() != null ? request.getTopMemoryProcess().getMemory() : null,
                formatProcessInsight(request.getTopMemoryProcess()),
                request.getCorrelationTimeline() != null ? request.getCorrelationTimeline() : List.of(),
                request.getIncidentGroupKey(),
                request.getIncidentStatus(),
                request.getSuspectedContributor(),
                request.getAffectedMetrics(),
                request.getConfidenceLevel(),
                request.getConfidenceScore(),
                request.getBaselineSummary(),
                request.getHistoricalPatternNotes(),
                request.getTimestamp()
        );
    }

    private String formatProcessInsight(AiInsightRequest.TopProcessSummary process) {
        if (process == null || process.getHumanExplanation() == null || process.getHumanExplanation().isBlank()) {
            return "No known plain-English mapping; use the raw process name only as supporting evidence.";
        }
        return "category=" + process.getCategory()
                + "; meaning=" + process.getHumanExplanation()
                + "; likely causes=" + safeList(process.getLikelyCauses())
                + "; recommended checks=" + safeList(process.getOperatorAdvice());
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }
}
