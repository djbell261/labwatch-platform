package com.example.aiengineservice.controller;

import com.example.aiengineservice.dto.ChatRequest;
import com.example.aiengineservice.dto.ChatResponse;
import com.example.aiengineservice.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/chat", "/api/ai/chat"})
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @Valid @RequestBody ChatRequest request
    ) {
        return new ChatResponse(
                chatService.generateChatResponse(
                        request.getMessage(),
                        authorizationHeader,
                        request.getMachineIdentifier()
                )
        );
    }
}
