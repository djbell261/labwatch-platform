package com.example.aiengineservice.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BedrockAiProvider implements AiProvider {

    private final String accessKeyId;
    private final String secretAccessKey;
    private final String region;
    private final String modelId;

    public BedrockAiProvider(
            @Value("${AWS_ACCESS_KEY_ID:}") String accessKeyId,
            @Value("${AWS_SECRET_ACCESS_KEY:}") String secretAccessKey,
            @Value("${AWS_REGION:us-east-1}") String region,
            @Value("${ai.bedrock.model-id:anthropic.claude-3-haiku-20240307-v1:0}") String modelId
    ) {
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.region = region;
        this.modelId = modelId;
    }

    @Override
    public String generateInsight(AiInsightRequest request) {
        if (accessKeyId == null || accessKeyId.isBlank() || secretAccessKey == null || secretAccessKey.isBlank()) {
            throw new IllegalStateException("AWS Bedrock credentials are not configured");
        }

        return "Bedrock provider is selected for region " + region + " and model " + modelId
                + ", but the current implementation is a placeholder until Bedrock request payload mapping is finalized.";
    }
}
