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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 팔자 + 등급 → 보살 톤 해석. Gemini 를 **한 번만** 호출하고 JSON 으로 받는다 (FR-GM-02, FR-GM-03).
 * <p>
 * 프롬프트에는 팔자와 등급만 들어간다. 생년월일·시간·닉네임은 이 클래스가 받지도 않는다 (FR-GM-01).
 * 실패(네트워크·429·파싱·필드 누락)는 1회 재시도 후 {@link ErrorCode#LLM_UNAVAILABLE} (FR-GM-04, FR-GM-05).
 * 로그에는 토큰 수·소요 시간만 남긴다 (FR-GM-06). "gemini ok" 로그 줄 수 = 호출 횟수 (NFR-O-06).
 */
@Slf4j
@Component
public class ReadingGenerator {

    private static final List<String> FIELDS =
            List.of("destinyDescription", "marriage", "children", "love");
    private static final String SYSTEM_PROMPT = loadResource("prompts/reading-system.txt");
    private static final Schema RESPONSE_SCHEMA = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(FIELDS.stream().collect(Collectors.toMap(f -> f, f -> Schema.builder().type(Type.Known.STRING).build())))
            .required(FIELDS)
            .build();
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final Client geminiClient;
    private final String model;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public ReadingGenerator(Client geminiClient, @Value("${gemini.model:gemini-3.6-flash}") String model) {
        this.geminiClient = geminiClient;
        this.model = model;
    }

    public Reading generate(SajuPillars pillars, Map<ReadingCategory, Grade> grades) {
        String prompt = buildPrompt(pillars, grades);
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return call(prompt);
            } catch (RuntimeException e) { // SDK 예외(ApiException·GenAiIOException)·Jackson·필드 누락 전부 unchecked
                log.warn("gemini failed. attempt={} type={} message={}", attempt, e.getClass().getSimpleName(), e.getMessage());
                if (e instanceof ApiException api && api.code() == 429) {
                    break; // 한도 초과는 바로 재시도해도 실패하고 한도만 더 태운다 (FR-GM-04)
                }
            }
        }
        throw new BusinessException(ErrorCode.LLM_UNAVAILABLE);
    }

    private Reading call(String prompt) {
        long start = System.nanoTime();
        GenerateContentResponse response = geminiClient.models.generateContent(model, prompt, GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_PROMPT)))
                .responseMimeType("application/json")
                .responseSchema(RESPONSE_SCHEMA)
                .temperature(0.9f)
                .thinkingConfig(ThinkingConfig.builder().thinkingLevel(ThinkingLevel.Known.MINIMAL)) // 30초 SLA. 문장 각색에 사고 토큰 불필요. 3.x 는 thinkingBudget 거부
                .build());
        log.info("gemini ok. ms={} tokens={}", (System.nanoTime() - start) / 1_000_000,
                response.usageMetadata().flatMap(u -> u.totalTokenCount()).orElse(-1));
        return parse(response.text());
    }

    /** 팔자와 등급뿐. 개인정보 미포함은 테스트로 고정한다 (TR-03). */
    static String buildPrompt(SajuPillars p, Map<ReadingCategory, Grade> grades) {
        StringBuilder sb = new StringBuilder()
                .append("년주 ").append(p.yearPillar())
                .append(", 월주 ").append(p.monthPillar())
                .append(", 일주 ").append(p.dayPillar())
                .append(", 시주 ").append(p.hourPillar() == null ? "모름" : p.hourPillar());
        for (ReadingCategory c : ReadingCategory.values()) {
            sb.append("\n").append(c.korean()).append(" 등급: ").append(grades.get(c).label());
        }
        return sb.toString();
    }

    Reading parse(String json) {
        if (json == null) {
            throw new IllegalStateException("empty response");
        }
        Map<String, String> m = mapper.readValue(json, STRING_MAP);
        for (String key : FIELDS) {
            if (m.get(key) == null || m.get(key).isBlank()) {
                throw new IllegalStateException("missing field: " + key);
            }
        }
        Map<ReadingCategory, String> contents = new EnumMap<>(ReadingCategory.class);
        contents.put(ReadingCategory.MARRIAGE, m.get("marriage"));
        contents.put(ReadingCategory.CHILDREN, m.get("children"));
        contents.put(ReadingCategory.LOVE, m.get("love"));
        return new Reading(m.get("destinyDescription"), contents);
    }

    private static String loadResource(String path) {
        try (InputStream in = ReadingGenerator.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
