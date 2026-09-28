package com.darkness.wks.saju;

import com.darkness.wks.common.config.GeminiConfig;
import com.darkness.wks.common.config.GeminiProperties;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.HttpOptions;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiRoutingTest {
    private final TestClock clock = new TestClock();

    @Test
    void roundRobinSkipsCooledAndExhaustedProjects() {
        var a = endpoint("a", null, 1, 100);
        var b = endpoint("b", null, 2, 100);
        var c = endpoint("c", null, 2, 100);
        var pool = new GeminiClientPool(List.of(a, b, c), null, 60, 1600, clock);
        assertThat(pool.acquireFree()).isSameAs(a);
        assertThat(pool.acquireFree()).isSameAs(b);
        pool.failed(b, new ApiException(503, "UNAVAILABLE", "ignored"));
        assertThat(pool.acquireFree()).isSameAs(c);
        assertThat(pool.acquireFree()).isSameAs(c);
        assertThat(pool.acquireFree()).isNull();
        clock.now = clock.now.plusSeconds(30);
        assertThat(pool.acquireFree()).isSameAs(b);
        assertThat(pool.acquireFree()).isNull();
    }

    @Test
    void concurrentReservationsRespectGlobalAndProjectLimits() throws Exception {
        var a = endpoint("a", null, 15, 100);
        var b = endpoint("b", null, 15, 100);
        var c = endpoint("c", null, 15, 100);
        var pool = new GeminiClientPool(List.of(a, b, c), null, 20, 1600, clock);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var jobs = new ArrayList<java.util.concurrent.Callable<Boolean>>();
            for (int i = 0; i < 100; i++) jobs.add(() -> pool.acquireFree() != null);
            int accepted = 0;
            for (var future : executor.invokeAll(jobs)) if (future.get()) accepted++;
            assertThat(accepted).isEqualTo(20);
            assertThat(a.budget.status()).startsWith("minute=7/15");
            assertThat(b.budget.status()).startsWith("minute=7/15");
            assertThat(c.budget.status()).startsWith("minute=6/15");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void quotaRetryDelayDailyLimitAndInvalidCredentialsAreRespected() {
        var a = endpoint("a", null, 15, 100);
        var pool = new GeminiClientPool(List.of(a), null, 60, 1600, clock);
        pool.failed(a, new ApiException(429, "RESOURCE_EXHAUSTED", "Details: "
                + "{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"120s\"}"));
        clock.now = clock.now.plusSeconds(60);
        assertThat(pool.acquireFree()).isNull();
        clock.now = clock.now.plusSeconds(60);
        assertThat(pool.acquireFree()).isSameAs(a);
        pool.failed(a, new ApiException(429, "RESOURCE_EXHAUSTED", "Details: "
                + "{\"violations\":[{\"quotaId\":\"GenerateRequestsPerDayPerProjectPerModel-FreeTier\"}]}"));
        clock.now = Instant.parse("2026-09-29T06:59:59Z");
        assertThat(pool.acquireFree()).isNull();
        clock.now = Instant.parse("2026-09-29T07:00:00Z");
        assertThat(pool.acquireFree()).isSameAs(a);
        pool.failed(a, new ApiException(403, "PERMISSION_DENIED", "secret must not be logged"));
        clock.now = clock.now.plusSeconds(86400);
        assertThat(pool.acquireFree()).isNull();
    }

    @Test
    void google503FallsBackOnceBut429AndMalformedJsonDoNot() throws Exception {
        try (FakeGemini server = new FakeGemini(); Client free = server.client("free"); Client paid = server.client("paid")) {
            server.status.put("free", 503);
            var json = json(free, paid, 60, 25_000, System::nanoTime);
            assertThat(json.generate("system", "synthetic", List.of("why"))).containsEntry("why", "ok");
            assertThat(server.count("free")).isEqualTo(1);
            assertThat(server.count("paid")).isEqualTo(1);

            server.status.put("free", 429);
            unavailable(json(free, paid, 60, 25_000, System::nanoTime));
            assertThat(server.count("free")).isEqualTo(2);
            assertThat(server.count("paid")).isEqualTo(1);

            server.status.put("free", 200);
            server.text = "not json";
            unavailable(json(free, paid, 60, 25_000, System::nanoTime));
            assertThat(server.count("free")).isEqualTo(3);
            assertThat(server.count("paid")).isEqualTo(1);
        }
    }

    @Test
    void paidFailureAndExhaustedGlobalBudgetNeverCauseThirdCall() throws Exception {
        try (FakeGemini server = new FakeGemini(); Client free = server.client("free"); Client paid = server.client("paid")) {
            server.status.put("free", 503);
            server.status.put("paid", 503);
            unavailable(json(free, paid, 60, 25_000, System::nanoTime));
            assertThat(server.count("free")).isEqualTo(1);
            assertThat(server.count("paid")).isEqualTo(1);
            unavailable(json(free, paid, 1, 25_000, System::nanoTime));
            assertThat(server.count("free")).isEqualTo(2);
            assertThat(server.count("paid")).isEqualTo(1);
        }
    }

    @Test
    void unavailablePaidAndExpiredDeadlineDoNotSendFallback() throws Exception {
        try (FakeGemini server = new FakeGemini(); Client free = server.client("free"); Client paid = server.client("paid")) {
            server.status.put("free", 503);
            unavailable(json(free, null, 60, 25_000, System::nanoTime));
            unavailable(json(free, paid, 60, 25_000,
                    () -> server.count("free") >= 2 ? 25_000_000_000L : 0));
            assertThat(server.count("free")).isEqualTo(2);
            assertThat(server.count("paid")).isZero();
        }
    }

    @Test
    void sdkTimeoutEndsCallWithoutRetryOrPaidFallback() throws Exception {
        try (FakeGemini server = new FakeGemini(); Client free = server.client("free"); Client paid = server.client("paid")) {
            server.delayMillis = 600;
            var pool = new GeminiClientPool(List.of(endpoint("free", free, 15, 100)),
                    endpoint("paid", paid, 5, 100), 60, 1600, clock);
            var json = new GeminiJson(pool, "model", 150, 2000, System::nanoTime);
            long start = System.nanoTime();
            unavailable(json);
            assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1500);
            assertThat(server.count("free")).isEqualTo(1);
            assertThat(server.count("paid")).isZero();
        }
    }

    @Test
    void springBindsKeysWithoutLegacyKeyAndRejectsDuplicates() {
        var runner = new ApplicationContextRunner().withUserConfiguration(GeminiConfig.class);
        runner.withPropertyValues("gemini.free-projects[0].api-key=first", "gemini.free-projects[1].api-key=second",
                "gemini.free-projects[2].api-key=third", "gemini.paid-project.api-key=paid")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(GeminiProperties.class);
                    assertThat(properties.activeFreeProjects()).hasSize(3);
                    assertThat(properties.toString()).doesNotContain("first", "second", "third");
                    var pool = context.getBean(GeminiClientPool.class);
                    assertThat(pool.acquireFree().alias).isEqualTo("free-1");
                    assertThat(pool.acquireFree().alias).isEqualTo("free-2");
                    assertThat(pool.acquireFree().alias).isEqualTo("free-3");
                });
        runner.withPropertyValues("gemini.api-key=legacy").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GeminiClientPool.class).acquireFree()).isNotNull();
        });
        runner.withPropertyValues("gemini.free-projects[0].api-key=duplicate",
                "gemini.paid-project.api-key=duplicate").run(context -> assertThat(context).hasFailed());
    }

    private GeminiClientPool.Endpoint endpoint(String alias, Client client, int rpm, int rpd) {
        return new GeminiClientPool.Endpoint(alias, client, rpm, rpd, clock);
    }

    private GeminiJson json(Client free, Client paid, int totalRpm, int totalMillis,
                            java.util.function.LongSupplier nanoTime) {
        var pool = new GeminiClientPool(List.of(endpoint("free", free, 15, 100)),
                paid == null ? null : endpoint("paid", paid, 5, 100), totalRpm, 1600, clock);
        return new GeminiJson(pool, "model", 12_000, totalMillis, nanoTime);
    }

    private void unavailable(GeminiJson json) {
        assertThatThrownBy(() -> json.generate("system", "synthetic", List.of("why")))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.LLM_UNAVAILABLE);
    }

    private static class TestClock extends Clock {
        Instant now = Instant.parse("2026-09-28T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }

    /** 외부 모델 대신 실제 SDK가 접속하는 로컬 HTTP 서버. */
    private static class FakeGemini implements AutoCloseable {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final Map<String, Integer> status = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
        volatile String text = "{\"why\":\"ok\"}";
        volatile long delayMillis;

        FakeGemini() throws Exception {
            server.createContext("/", exchange -> {
                String key = exchange.getRequestHeaders().getFirst("x-goog-api-key");
                calls.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                try {
                    if (delayMillis > 0) java.lang.Thread.sleep(delayMillis);
                    int code = status.getOrDefault(key, 200);
                    String body = code == 200
                            ? "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                                    + tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(text) + "}]}}]}"
                            : "{\"error\":{\"code\":" + code + ",\"status\":\"UNAVAILABLE\",\"message\":\"synthetic\"}}";
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(code, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (InterruptedException interrupted) {
                    java.lang.Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
        }

        Client client(String key) {
            return Client.builder().apiKey(key).httpOptions(HttpOptions.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build()).build();
        }

        int count(String key) { return calls.getOrDefault(key, new AtomicInteger()).get(); }
        @Override public void close() { server.stop(0); }
    }
}
