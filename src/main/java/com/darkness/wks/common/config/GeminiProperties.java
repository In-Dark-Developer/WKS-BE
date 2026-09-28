package com.darkness.wks.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ConfigurationProperties("gemini")
public record GeminiProperties(
        String apiKey,
        @DefaultValue("gemini-3.5-flash-lite") String model,
        @DefaultValue("12") int timeoutSeconds,
        @DefaultValue("25") int totalTimeoutSeconds,
        List<Project> freeProjects,
        Project paidProject,
        // 유료 RPD 중 사주만 쓸 수 있게 남겨 두는 호출 수. 다른 파트는 유료 잔량이 이보다 많을 때만 쓴다
        @DefaultValue("42") int paidSajuReservePerDay
) {
    public GeminiProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        freeProjects = freeProjects == null ? List.of() : List.copyOf(freeProjects);
        if (model == null || model.isBlank() || freeProjects.size() > 6 || timeoutSeconds < 1 || totalTimeoutSeconds < 1
                || timeoutSeconds > 30 || totalTimeoutSeconds > 30 || paidSajuReservePerDay < 0) {
            throw new IllegalArgumentException("Invalid Gemini limits");
        }
        Set<String> keys = new HashSet<>();
        List<Project> active = freeProjects.stream().filter(Project::configured).toList();
        if (active.isEmpty() && !apiKey.isBlank()) {
            keys.add(apiKey);
        }
        for (Project project : active) {
            if (!keys.add(project.apiKey())) {
                throw new IllegalArgumentException("Duplicate Gemini key configuration");
            }
        }
        if (paidProject != null && paidProject.configured() && !keys.add(paidProject.apiKey())) {
            throw new IllegalArgumentException("Duplicate Gemini key configuration");
        }
    }

    public List<Project> activeFreeProjects() {
        List<Project> active = freeProjects.stream().filter(Project::configured).toList();
        return !active.isEmpty() || apiKey.isBlank() ? active : List.of(new Project(apiKey, 15, 500));
    }

    @Override
    public String toString() {
        return "GeminiProperties[credentials=redacted]";
    }

    public record Project(String apiKey, @DefaultValue("15") int maxPerMinute,
                          @DefaultValue("500") int maxPerDay) {
        public Project {
            apiKey = apiKey == null ? "" : apiKey.trim();
            if (maxPerMinute < 1 || maxPerDay < 1) {
                throw new IllegalArgumentException("Invalid Gemini project limits");
            }
        }

        public boolean configured() {
            return !apiKey.isBlank();
        }

        @Override
        public String toString() {
            return "Project[credentials=redacted]";
        }
    }
}
