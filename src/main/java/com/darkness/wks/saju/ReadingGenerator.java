package com.darkness.wks.saju;

import com.darkness.wks.common.Gender;
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
    private final CallBudget budget;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @org.springframework.beans.factory.annotation.Autowired // 생성자가 둘이라 스프링용을 명시
    public ReadingGenerator(Client geminiClient,
                            @Value("${gemini.model:gemini-3.5-flash-lite}") String model,
                            @Value("${gemini.max-per-minute:60}") int maxPerMinute,
                            @Value("${gemini.max-per-day:1600}") int maxPerDay) {
        this.geminiClient = geminiClient;
        this.model = model;
        this.budget = new CallBudget(maxPerMinute, maxPerDay, java.time.Clock.systemUTC());
    }

    /** 테스트·스모크용. 상한은 기본값(60/1,600) */
    ReadingGenerator(Client geminiClient, String model) {
        this(geminiClient, model, 60, 1600);
    }

    /** 시스템 프롬프트·모델이 바뀌면 달라진다. 저장된 해석 재사용 여부 판단용 (#62) */
    public int promptVersion() {
        return (SYSTEM_PROMPT + "|" + model).hashCode();
    }

    public Reading generate(SajuPillars pillars, Map<ReadingCategory, Grade> grades, Gender gender) {
        String prompt = buildPrompt(pillars, grades, gender);
        for (int attempt = 1; attempt <= 2; attempt++) {
            if (!budget.tryAcquire()) { // 총량 상한. 재시도도 한도를 쓰므로 시도마다 확인 (#64)
                log.warn("gemini budget exhausted. {}", budget.status());
                break;
            }
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

    /** 프롬프트용 오행 이름. 시스템 프롬프트의 "나무·불·흙·쇠·물의 기운"과 맞춘다 */
    private static final String[] PLAIN = {"나무", "불", "흙", "쇠", "물"};

    /**
     * 팔자·등급·성별·오행 사실뿐. 개인정보 미포함은 테스트로 고정한다 (TR-03).
     * 오행 사실(나의 기운·많은/없는 기운·배우자·자녀 기운)을 코드가 정해 넘긴다. 팔자 글자만 주면 LLM 이 매번 다른 오행을 집어 말한다 (#48)
     */
    static String buildPrompt(SajuPillars p, Map<ReadingCategory, Grade> grades, Gender gender) {
        Element me = Element.ofStem(p.dayPillar().charAt(0));
        // 강한/약한 기운은 화면(ResultResponse.ElementResponse)과 같은 글자 개수 기준이다 (#70).
        // 자리 가중치(Element.strengths)로 고르면 "수 3개인데 왜 화 얘기?" 가 19% 에서 생긴다. 점수·행운 장소는 가중치 그대로
        int[] count = new int[5];
        for (String pillar : List.of(p.yearPillar(), p.monthPillar(), p.dayPillar(),
                p.hourPillar() == null ? "" : p.hourPillar())) {
            if (pillar.isEmpty()) continue;
            count[Element.ofStem(pillar.charAt(0)).ordinal()]++;
            count[Element.ofBranch(pillar.charAt(1)).ordinal()]++;
        }
        int max = java.util.Arrays.stream(count).max().orElse(0);
        String strong = java.util.Arrays.stream(Element.values()).filter(e -> count[e.ordinal()] == max)
                .map(e -> PLAIN[e.ordinal()]).collect(Collectors.joining(", "));
        String weak = java.util.Arrays.stream(Element.values()).filter(e -> count[e.ordinal()] == 0)
                .map(e -> PLAIN[e.ordinal()]).collect(Collectors.joining(", "));
        int spouseRole = gender == Gender.MALE ? 2 : 3; // 남 재성, 여 관성
        int childRole = gender == Gender.MALE ? 3 : 1;  // 남 관성, 여 식상
        Element spouse = null, child = null;
        for (Element e : Element.values()) {
            if (e.roleFor(me) == spouseRole) spouse = e;
            if (e.roleFor(me) == childRole) child = e;
        }
        StringBuilder sb = new StringBuilder()
                .append("성별 ").append(gender == Gender.MALE ? "남성" : "여성").append("\n")
                .append("년주 ").append(p.yearPillar())
                .append(", 월주 ").append(p.monthPillar())
                .append(", 일주 ").append(p.dayPillar())
                .append(", 시주 ").append(p.hourPillar() == null ? "모름" : p.hourPillar()).append("\n")
                .append("나의 기운(태어난 날의 기운): ").append(PLAIN[me.ordinal()]).append("\n")
                .append("많은 기운: ").append(strong).append(" / 없는 기운: ").append(weak.isEmpty() ? "없음" : weak).append("\n")
                .append("배우자 기운: ").append(PLAIN[spouse.ordinal()])
                .append(" / 배우자 자리의 기운: ").append(PLAIN[Element.ofBranch(p.dayPillar().charAt(1)).ordinal()]).append("\n")
                .append("자녀 기운: ").append(PLAIN[child.ordinal()]);
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
