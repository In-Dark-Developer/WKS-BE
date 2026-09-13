package com.darkness.wks.saju;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Gemini 를 1회 호출하는 수동 스모크. 무료 한도를 쓰므로 CI 에서는 돌지 않는다 (TR-E-01).
 * 실행: GOOGLE_API_KEY=... ./gradlew test --tests '*GeminiSmokeTest'
 */
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
class GeminiSmokeTest {

    @Test
    void generatesReadingWithAllFields() {
        Client client = Client.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .httpOptions(HttpOptions.builder().timeout(30_000).build())
                .build();
        ReadingGenerator generator = new ReadingGenerator(client, "gemini-3.5-flash-lite");

        Reading r = generator.generate(
                new SajuPillars("임오", "계묘", "신사", "을미"),
                Map.of(ReadingCategory.MARRIAGE, Grade.SS, ReadingCategory.CHILDREN, Grade.A_PLUS, ReadingCategory.LOVE, Grade.B));

        System.out.println(r);
        assertThat(r.destinyDescription()).isNotBlank();
        assertThat(r.contents()).containsKeys(ReadingCategory.MARRIAGE, ReadingCategory.CHILDREN, ReadingCategory.LOVE);
    }
}
