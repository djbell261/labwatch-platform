package com.example.aiengineservice.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnownProcessEnrichmentServiceTest {

    private final KnownProcessEnrichmentService service = new KnownProcessEnrichmentService();

    @Test
    void enrichesAppleVirtualizationProcess() {
        ProcessInsight insight = service.enrich("com.apple.Virtualization.VirtualMachine").orElseThrow();

        assertEquals("Virtualization / Containers", insight.category());
        assertTrue(insight.humanExplanation().contains("Docker containers"));
        assertTrue(insight.likelyCauses().contains("OrbStack"));
        assertTrue(insight.operatorAdvice().contains("Inspect docker stats."));
        assertTrue(insight.beginnerFriendly());
    }

    @Test
    void enrichesChromeRendererProcess() {
        ProcessInsight insight = service.enrich("Google Chrome Helper (Renderer)").orElseThrow();

        assertEquals("Browser workload", insight.category());
        assertTrue(insight.humanExplanation().contains("Chrome browser tab or extension"));
        assertTrue(insight.likelyCauses().contains("developer tools"));
    }

    @Test
    void enrichesPgAdminProcess() {
        ProcessInsight insight = service.enrich("pgAdmin 4 Helper (Renderer)").orElseThrow();

        assertEquals("Local database application", insight.category());
        assertTrue(insight.humanExplanation().contains("database management app"));
        assertTrue(insight.likelyCauses().contains("running queries"));
        assertTrue(insight.operatorAdvice().contains("Close unused pgAdmin tabs."));
    }

    @Test
    void unknownProcessFallsBackToEmpty() {
        assertFalse(service.enrich("some-custom-daemon").isPresent());
    }
}
