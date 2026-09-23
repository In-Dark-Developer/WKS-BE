package com.darkness.wks.saju;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import com.google.genai.types.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gemini 를 **한 번** 호출해 문자열 필드만 있는 JSON 을 받는다. 사주 해석과 궁합 이유가 함께 쓴다.
 * <p>
 * 실패(네트워크·429·파싱·필드 누락)는 1회 재시도 후 {@link ErrorCode#LLM_UNAVAILABLE} (FR-GM-03~05).
 * 총량 상한 {@link CallBudget} 은 여기 하나뿐이라 두 생성기가 한 한도를 나눠 쓴다 (FR-CP-14).
 * 로그에는 토큰 수·소요 시간만 남긴다 (FR-GM-06). "gemini ok" 로그 줄 수 = 호출 횟수 (NFR-O-06).
 */
@Slf4j
@Component
public class GeminiJson {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final Client geminiClient;
    private final String model;
    private final CallBudget budget;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @org.springframework.beans.factory.annotation.Autowired // 생성자가 둘이라 스프링용을 명시
    public GeminiJson(Client geminiClient,
                      @Value("${gemini.model:gemini-3.5-flash-lite}") String model,
                      @Value("${gemini.max-per-minute:60}") int maxPerMinute,
                      @Value("${gemini.max-per-day:1600}") int maxPerDay) {
        this.geminiClient = geminiClient;
        this.model = model;
        this.budget = new CallBudget(maxPerMinute, maxPerDay, java.time.Clock.systemUTC());
    }

    /** 테스트·스모크용. 상한은 기본값(60/1,600) */
    GeminiJson(Client geminiClient, String model) {
        this(geminiClient, model, 60, 1600);
    }

    String model() {
        return model;
    }

    /** 모든 필드가 비어 있지 않은 문자열 JSON. 아니면 {@link ErrorCode#LLM_UNAVAILABLE} */
    Map<String, String> generate(String systemPrompt, String prompt, List<String> fields) {
        Schema schema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(fields.stream().collect(Collectors.toMap(f -> f, f -> Schema.builder().type(Type.Known.STRING).build())))
                .required(fields)
                .build();
        for (int attempt = 1; attempt <= 2; attempt++) {
            if (!budget.tryAcquire()) { // 총량 상한. 재시도도 한도를 쓰므로 시도마다 확인 (#64)
                log.warn("gemini budget exhausted. {}", budget.status());
                break;
            }
            try {
                return parse(call(systemPrompt, prompt, schema), fields);
            } catch (RuntimeException e) { // SDK 예외(ApiException·GenAiIOException)·Jackson·필드 누락 전부 unchecked
                log.warn("gemini failed. attempt={} type={} message={}", attempt, e.getClass().getSimpleName(), e.getMessage());
                if (e instanceof ApiException api && api.code() == 429) {
                    break; // 한도 초과는 바로 재시도해도 실패하고 한도만 더 태운다 (FR-GM-04)
                }
            }
        }
        throw new BusinessException(ErrorCode.LLM_UNAVAILABLE);
    }

    private String call(String systemPrompt, String prompt, Schema schema) {
        long start = System.nanoTime();
        GenerateContentResponse response = geminiClient.models.generateContent(model, prompt, GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                .responseMimeType("application/json")
                .responseSchema(schema)
                .temperature(0.9f)
                .thinkingConfig(ThinkingConfig.builder().thinkingLevel(ThinkingLevel.Known.MINIMAL)) // 30초 SLA. 문장 각색에 사고 토큰 불필요. 3.x 는 thinkingBudget 거부
                .build());
        log.info("gemini ok. ms={} tokens={}", (System.nanoTime() - start) / 1_000_000,
                response.usageMetadata().flatMap(u -> u.totalTokenCount()).orElse(-1));
        return response.text();
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
