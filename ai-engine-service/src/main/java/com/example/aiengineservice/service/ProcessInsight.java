package com.example.aiengineservice.service;

import java.util.List;

public record ProcessInsight(
        String category,
        String humanExplanation,
        List<String> likelyCauses,
        List<String> operatorAdvice,
        boolean beginnerFriendly
) {
}
