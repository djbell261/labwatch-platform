package com.example.aiengineservice.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class KnownProcessEnrichmentService {

    private static final List<KnownProcessMapping> MAPPINGS = List.of(
            new KnownProcessMapping(
                    List.of("com.apple.virtualization.virtualmachine", "virtualization.virtualmachine", "orbstack", "docker desktop"),
                    new ProcessInsight(
                            "Virtualization / Containers",
                            "Docker containers, local virtual machines, or development environments are likely running heavily in the background.",
                            List.of("Docker Desktop", "OrbStack", "Kafka/Postgres containers", "local Kubernetes", "backend rebuilds"),
                            List.of("Check active containers.", "Stop unused services.", "Inspect docker stats.", "Reduce simultaneous local workloads."),
                            true
                    )
            ),
            new KnownProcessMapping(
                    List.of("google chrome helper", "google chrome", "chrome"),
                    new ProcessInsight(
                            "Browser workload",
                            "A Chrome browser tab or extension is consuming unusually high system resources.",
                            List.of("many open tabs", "video playback", "heavy web applications", "developer tools", "browser extensions"),
                            List.of("Close unused tabs.", "Check Chrome Task Manager.", "Pause video or screen-sharing tabs.", "Disable suspicious extensions."),
                            true
                    )
            ),
            new KnownProcessMapping(
                    List.of("docker", "containerd", "com.docker"),
                    new ProcessInsight(
                            "Containers",
                            "Containerized development services are likely using resources in the background.",
                            List.of("running databases", "image builds", "local services", "test environments"),
                            List.of("Check running containers.", "Stop containers that are not needed.", "Inspect docker stats.", "Clean up stale build work carefully."),
                            true
                    )
            ),
            new KnownProcessMapping(
                    List.of("intellij", "idea", "gradle", "java"),
                    new ProcessInsight(
                            "Development tooling / JVM",
                            "A Java application, build, test run, or IDE task is likely doing heavy work.",
                            List.of("IDE indexing", "Gradle builds", "test runs", "large Java services", "JVM memory pressure"),
                            List.of("Check active builds or tests.", "Wait for indexing to finish if it is temporary.", "Stop unused local services.", "Review JVM or build settings if it repeats."),
                            true
                    )
            ),
            new KnownProcessMapping(
                    List.of("node", "vite", "npm"),
                    new ProcessInsight(
                            "Frontend / Node tooling",
                            "A Node.js development server, frontend build, or JavaScript tool is likely consuming resources.",
                            List.of("Vite dev server", "npm scripts", "frontend rebuilds", "test watchers"),
                            List.of("Check active npm or dev-server tasks.", "Stop unused watchers.", "Restart the dev server if usage keeps climbing."),
                            true
                    )
            ),
            new KnownProcessMapping(
                    List.of("python"),
                    new ProcessInsight(
                            "Python workload",
                            "A Python script, agent, or data-processing task is likely doing active work.",
                            List.of("long-running scripts", "data processing", "local agents", "test runs"),
                            List.of("Confirm which script is running.", "Stop duplicate jobs.", "Review logs if CPU stays high."),
                            true
                    )
            ),
            new KnownProcessMapping(
                    List.of("pgadmin"),
                    new ProcessInsight(
                            "Local database application",
                            "A local database management app is actively processing tasks in the background.",
                            List.of("running queries", "refreshing dashboards", "browsing large datasets", "managing local databases"),
                            List.of("Close unused pgAdmin tabs.", "Stop inactive containers.", "Monitor CPU over the next few minutes."),
                            true
                    )
            ),
            new KnownProcessMapping(
                    List.of("postgres", "postgresql", "kafka", "redis"),
                    new ProcessInsight(
                            "Local infrastructure service",
                            "A local database, broker, or cache service is likely handling background development workload.",
                            List.of("Postgres queries", "Kafka brokers", "Redis cache activity", "integration tests"),
                            List.of("Check the related local service.", "Stop unused development infrastructure.", "Review container or service logs if usage persists."),
                            true
                    )
            )
    );

    public Optional<ProcessInsight> enrich(String processName) {
        if (processName == null || processName.isBlank()) {
            return Optional.empty();
        }

        String normalized = processName.trim().toLowerCase(Locale.ROOT);
        return MAPPINGS.stream()
                .filter(mapping -> mapping.matches(normalized))
                .map(KnownProcessMapping::insight)
                .findFirst();
    }

    private record KnownProcessMapping(List<String> aliases, ProcessInsight insight) {
        private boolean matches(String processName) {
            return aliases.stream().anyMatch(processName::contains);
        }
    }
}
