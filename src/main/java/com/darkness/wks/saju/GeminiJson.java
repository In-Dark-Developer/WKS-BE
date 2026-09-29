package com.darkness.wks.saju;

import com.darkness.wks.common.config.GeminiProperties;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import com.google.genai.types.Type;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gemini 를 **한 번** 호출해 문자열 필드만 있는 JSON 을 받는다. 사주 해석과 궁합 이유가 함께 쓴다.
 * <p>
 * 무료 쪽이 Google 503(과부하)·429(한도 소진)·IO 실패(타임아웃 포함)로 실패하거나 무료 키가 모두 쓸 수 없으면 유료 프로젝트로
 * 1회 전환한다. 유료 비용은 유료 RPD·사주 예약분이 막는다. 그 외 실패(파싱·4xx)는 즉시 {@link ErrorCode#LLM_UNAVAILABLE} (FR-GM-03~05).
 * 프로젝트별·전체 {@link CallBudget}을 사주·친구 궁합·소개팅 생성기가 함께 쓴다 (FR-CP-14).
 * 유료 대체는 {@link LlmPurpose} 별 예약분을 본다 — 소개팅 등이 유료 한도를 다 써서 사주 결과가 막히지 않게.
 * 로그에는 토큰 수·소요 시간만 남긴다 (FR-GM-06). "gemini ok/failed"로 검증 성공·실패한 시도를 구별한다 (NFR-O-06).
 */
@Slf4j
@Component
public class GeminiJson {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final GeminiClientPool pool;
    private final String model;
    private final int timeoutMillis;
    private final long totalTimeoutNanos;
    private final LongSupplier nanoTime;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    public GeminiJson(GeminiClientPool pool, GeminiProperties properties) {
        this(pool, properties.model(), properties.timeoutSeconds() * 1000,
                properties.totalTimeoutSeconds() * 1000, System::nanoTime);
    }

    GeminiJson(GeminiClientPool pool, String model, int timeoutMillis, int totalTimeoutMillis,
               LongSupplier nanoTime) {
        this.pool = pool;
        this.model = model;
        this.timeoutMillis = timeoutMillis;
        this.totalTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(totalTimeoutMillis);
        this.nanoTime = nanoTime;
    }

    /** 기존 파싱 테스트·수동 스모크도 같은 예산과 재시도 정책을 사용한다. */
    GeminiJson(Client client, String model) {
        this(new GeminiClientPool(List.of(new GeminiClientPool.Endpoint("free-1", client, 15, 1600,
                Clock.systemUTC())), null, Clock.systemUTC()), model, 12_000, 25_000, System::nanoTime);
    }

    String model() {
        return model;
    }

    /** 모든 필드가 비어 있지 않은 문자열 JSON. 아니면 {@link ErrorCode#LLM_UNAVAILABLE} */
    public Map<String, String> generate(LlmPurpose purpose, String systemPrompt, String prompt, List<String> fields) {
        Schema schema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(fields.stream().collect(Collectors.toMap(f -> f, f -> Schema.builder().type(Type.Known.STRING).build())))
                .required(fields)
                .build();
        long started = nanoTime.getAsLong();
        GeminiClientPool.Endpoint endpoint = pool.acquireFree();
        boolean onPaid = false;
        if (endpoint == null) {
            // 무료 키가 모두 하루 한도·쿨다운·비활성. 무료 요청을 보내 봐야 429 뿐이라 바로 유료로 간다
            endpoint = pool.acquirePaid(purpose);
            onPaid = true;
            if (endpoint != null) log.info("gemini fallback. purpose={} project=paid reason=free-unavailable", purpose);
        }
        for (int attempt = 1; endpoint != null && attempt <= 2; attempt++) {
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(totalTimeoutNanos - (nanoTime.getAsLong() - started));
            if (remainingMillis < 1000) break;
            try {
                long callStarted = nanoTime.getAsLong();
                GenerateContentResponse response = call(endpoint, systemPrompt, prompt, schema,
                        (int) Math.min(timeoutMillis, remainingMillis));
                // 전체 시간 예산은 새 시도를 시작할지만 정한다. 이미 받은 정상 응답은 늦어도 쓴다.
                Map<String, String> result = parse(response.text(), fields);
                pool.succeeded(endpoint);
                // 입력·출력 단가가 8배 넘게 달라서 유료 한도(원 → 호출 수) 환산에 둘을 나눠 남긴다. 출력에 사고 토큰 포함
                var usage = response.usageMetadata();
                log.info("gemini ok. purpose={} project={} attempt={} ms={} tokens={} in={} out={}", purpose,
                        endpoint.alias, attempt, (nanoTime.getAsLong() - callStarted) / 1_000_000,
                        usage.flatMap(u -> u.totalTokenCount()).orElse(-1),
                        usage.flatMap(u -> u.promptTokenCount()).orElse(-1),
                        usage.flatMap(u -> u.candidatesTokenCount()).orElse(0)
                                + usage.flatMap(u -> u.thoughtsTokenCount()).orElse(0));
                return result;
            } catch (RuntimeException error) {
                int code = error instanceof ApiException api ? api.code() : 0;
                log.warn("gemini failed. purpose={} project={} attempt={} code={} type={} ms={}", purpose, endpoint.alias, attempt,
                        code, error.getClass().getSimpleName(), (nanoTime.getAsLong() - started) / 1_000_000);
                if (error instanceof ApiException api) pool.failed(endpoint, api);
                else if (error instanceof GenAiIOException) pool.timedOut(endpoint);
                // 무료 과부하(503)·한도(429)·무응답(IO, 운영 실패의 34%)만 유료로 넘긴다. 파싱·4xx 는 유료도 같다.
                // 유료에서 실패하면 다시 시도하지 않는다 (요청당 HTTP 최대 2회)
                if (!onPaid && (code == 503 || code == 429 || error instanceof GenAiIOException)
                        && totalTimeoutNanos - (nanoTime.getAsLong() - started) >= TimeUnit.SECONDS.toNanos(1)) {
                    endpoint = pool.acquirePaid(purpose);
                    onPaid = true;
                    if (endpoint != null) log.info("gemini fallback. purpose={} project=paid reason={}", purpose,
                            code != 0 ? code : error.getClass().getSimpleName());
                } else {
                    break;
                }
            }
        }
        throw new BusinessException(ErrorCode.LLM_UNAVAILABLE);
    }

    private GenerateContentResponse call(GeminiClientPool.Endpoint endpoint, String systemPrompt, String prompt,
                                         Schema schema, int timeout) {
        return endpoint.client.models.generateContent(model, prompt,
                GenerateContentConfig.builder()
                        .httpOptions(HttpOptions.builder().timeout(timeout)
                                .retryOptions(HttpRetryOptions.builder().attempts(1)))
                        .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                        .responseMimeType("application/json")
                        .responseSchema(schema)
                        .temperature(0.9f)
                        .thinkingConfig(ThinkingConfig.builder().thinkingLevel(ThinkingLevel.Known.MINIMAL))
                        .build());
    }

    Map<String, String> parse(String json, List<String> fields) {
        if (json == null) {
            throw new IllegalStateException("empty response");
        }
        Map<String, String> m = mapper.readValue(json, STRING_MAP);
        for (String key : fields) {
            if (m.get(key) == null || m.get(key).isBlank()) {
                throw new IllegalStateException("missing field: " + key);
            }
        }
        return m;
    }

    static String loadResource(String path) {
        try (InputStream in = GeminiJson.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
