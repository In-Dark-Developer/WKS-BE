package com.darkness.wks.saju;

import com.darkness.wks.common.config.GeminiProperties;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.errors.ApiException;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 프로젝트 선택과 예산 예약만 잠근다. 외부 호출은 이 락 밖에서 실행한다. */
@Slf4j
public final class GeminiClientPool implements AutoCloseable {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final List<Endpoint> free;
    private final Endpoint paid;
    private final CallBudget total;
    private final Clock clock;
    private final List<Client> ownedClients = new ArrayList<>();
    private int cursor;

    public GeminiClientPool(GeminiProperties properties, Client primary) {
        clock = Clock.systemUTC();
        total = new CallBudget(properties.maxPerMinute(), properties.maxPerDay(), clock);
        free = new ArrayList<>();
        List<GeminiProperties.Project> projects = properties.activeFreeProjects();
        for (int i = 0; i < projects.size(); i++) {
            GeminiProperties.Project project = projects.get(i);
            Client client = i == 0 ? primary : create(project, properties.timeoutSeconds());
            free.add(new Endpoint("free-" + (i + 1), client, project.maxPerMinute(), project.maxPerDay(), clock));
        }
        GeminiProperties.Project project = properties.paidProject();
        paid = project != null && project.configured()
                ? new Endpoint("paid", create(project, properties.timeoutSeconds()),
                        project.maxPerMinute(), project.maxPerDay(), clock) : null;
    }

    GeminiClientPool(List<Endpoint> free, Endpoint paid, int rpm, int rpd, Clock clock) {
        this.free = List.copyOf(free);
        this.paid = paid;
        this.total = new CallBudget(rpm, rpd, clock);
        this.clock = clock;
    }

    public static Client createClient(String apiKey, int timeoutSeconds) {
        return Client.builder().apiKey(apiKey)
                .httpOptions(HttpOptions.builder().timeout(timeoutSeconds * 1000)
                        // SDK 재시도는 앱 예산에 잡히지 않으므로 실제 시도를 앱에서만 결정한다.
                        .retryOptions(HttpRetryOptions.builder().attempts(1)).build())
                .build();
    }

    private Client create(GeminiProperties.Project project, int timeoutSeconds) {
        Client client = createClient(project.apiKey(), timeoutSeconds);
        ownedClients.add(client);
        return client;
    }

    synchronized Endpoint acquireFree() {
        for (int i = 0; i < free.size(); i++) {
            int index = (cursor + i) % free.size();
            Endpoint endpoint = free.get(index);
            if (reserve(endpoint)) {
                cursor = (index + 1) % free.size();
                return endpoint;
            }
        }
        log.warn("gemini pool unavailable. kind=free {}", total.status());
        return null;
    }

    synchronized Endpoint acquirePaid() {
        if (paid != null && reserve(paid)) return paid;
        log.warn("gemini pool unavailable. kind=paid configured={} {}", paid != null, total.status());
        return null;
    }

    private boolean reserve(Endpoint endpoint) {
        if (endpoint.disabled || clock.instant().isBefore(endpoint.blockedUntil)
                || !endpoint.budget.available() || !total.available()) return false;
        endpoint.budget.tryAcquire();
        total.tryAcquire();
        return true;
    }

    synchronized void failed(Endpoint endpoint, ApiException error) {
        if (error.code() == 401 || error.code() == 403) endpoint.disabled = true;
        Instant until = switch (error.code()) {
            case 503 -> clock.instant().plusSeconds(30);
            case 429 -> quotaReset(error);
            default -> clock.instant();
        };
        if (until.isAfter(endpoint.blockedUntil)) endpoint.blockedUntil = until;
    }

    private Instant quotaReset(ApiException error) {
        Instant until = clock.instant().plusSeconds(60);
        // SDK 1.70은 구조화된 details를 보존하지 않고 message의 Details: 뒤 JSON 줄로 전달한다.
        // 파싱 불가 시에도 60초 쉬며, 원문에는 키가 들어갈 수 있어 로그에 쓰지 않는다.
        String message = error.message();
        if (message == null || !message.contains("Details: ")) return until;
        for (String line : message.substring(message.indexOf("Details: ") + 9).split("\\R")) {
            try {
                JsonNode detail = MAPPER.readTree(line);
                if (detail.path("@type").asString().endsWith("google.rpc.RetryInfo")) {
                    String delay = detail.path("retryDelay").asString();
                    if (delay.endsWith("s")) {
                        double seconds = Double.parseDouble(delay.substring(0, delay.length() - 1));
                        if (Double.isFinite(seconds) && seconds > 0 && seconds <= 86400) {
                            Instant retry = clock.instant().plusMillis((long) Math.ceil(seconds * 1000));
                            if (retry.isAfter(until)) until = retry;
                        }
                    }
                }
                for (JsonNode violation : detail.path("violations")) {
                    if (violation.path("quotaId").asString().contains("PerDay")) {
                        Instant midnight = clock.instant().atZone(CallBudget.PACIFIC).toLocalDate().plusDays(1)
                                .atStartOfDay(CallBudget.PACIFIC).toInstant();
                        if (midnight.isAfter(until)) until = midnight;
                    }
                }
            } catch (RuntimeException ignored) {
                // SDK/서버 오류 형식이 달라져도 기본 쿨다운은 적용한다.
            }
        }
        return until;
    }

    @Override
    public void close() {
        // 첫 Client는 Spring이 관리하므로 여기서는 추가로 만든 Client만 닫는다.
        ownedClients.forEach(Client::close);
    }

    static final class Endpoint {
        final String alias;
        final Client client;
        final CallBudget budget;
        private Instant blockedUntil = Instant.MIN;
        private boolean disabled;

        Endpoint(String alias, Client client, int rpm, int rpd, Clock clock) {
            this.alias = alias;
            this.client = client;
            this.budget = new CallBudget(rpm, rpd, clock);
        }
    }
}
