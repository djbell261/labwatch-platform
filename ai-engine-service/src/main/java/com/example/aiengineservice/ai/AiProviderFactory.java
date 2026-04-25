package com.example.aiengineservice.ai;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class AiProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(AiProviderFactory.class);

    private final MockAiProvider mockAiProvider;
    private final OpenAiProvider openAiProvider;
    private final BedrockAiProvider bedrockAiProvider;
    private final String providerName;

    public AiProviderFactory(
            MockAiProvider mockAiProvider,
            OpenAiProvider openAiProvider,
            BedrockAiProvider bedrockAiProvider,
            @Value("${ai.provider:mock}") String providerName
    ) {
        this.mockAiProvider = mockAiProvider;
        this.openAiProvider = openAiProvider;
        this.bedrockAiProvider = bedrockAiProvider;
        this.providerName = providerName;
    }

    @Bean
    public AiProvider aiProvider() {
        return createProvider();
    }

    public AiProvider createProvider() {
        return switch (providerName == null ? "mock" : providerName.trim().toLowerCase()) {
            case "openai" -> openAiProvider;
            case "bedrock" -> bedrockAiProvider;
            default -> mockAiProvider;
        };
    }

    @PostConstruct
    public void logActiveProvider() {
        AiProvider provider = createProvider();
        log.info("AI provider active: {}", provider.getClass().getSimpleName());
    }
}
