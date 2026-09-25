package com.darkness.wks.dating;

import com.darkness.wks.saju.Element;
import com.darkness.wks.saju.GeminiJson;
import com.darkness.wks.saju.SajuPillars;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 소개팅 카드 전용 문장. 친구 궁합의 세 답과 별도로 생성한다. */
@Component
public class DatingReasonGenerator {

    private static final String SYSTEM_PROMPT = loadPrompt();
    private final GeminiJson gemini;

    public DatingReasonGenerator(GeminiJson gemini) {
        this.gemini = gemini;
    }

    public String generate(SajuPillars viewer, SajuPillars candidate, int score, String tier) {
        return gemini.generate(SYSTEM_PROMPT, buildPrompt(viewer, candidate, score, tier),
                List.of("reason")).get("reason");
    }

    static String buildPrompt(SajuPillars viewer, SajuPillars candidate, int score, String tier) {
        Element mine = Element.ofStem(viewer.dayPillar().charAt(0));
        Element theirs = Element.ofStem(candidate.dayPillar().charAt(0));
        String relation;
        if (mine == theirs) {
            relation = "같은 기운";
        } else if (mine.generates(theirs)) {
            relation = "사용자의 기운이 상대의 기운을 살림(상생)";
        } else if (theirs.generates(mine)) {
            relation = "상대의 기운이 사용자의 기운을 살림(상생)";
        } else if (mine.controls(theirs)) {
            relation = "사용자의 기운이 상대의 기운을 누름(상극)";
        } else {
            relation = "상대의 기운이 사용자의 기운을 누름(상극)";
        }
        return "관계 유형: " + tier + " / 궁합 점수: " + score + "\n"
                + "사용자 팔자: " + pillars(viewer) + " / 일간 기운: " + mine.korean() + "\n"
                + "상대 팔자: " + pillars(candidate) + " / 일간 기운: " + theirs.korean() + "\n"
                + "두 기운의 관계: " + relation;
    }

    private static String pillars(SajuPillars pillars) {
        return pillars.yearPillar() + " " + pillars.monthPillar() + " " + pillars.dayPillar()
                + (pillars.hourPillar() == null ? "" : " " + pillars.hourPillar());
    }

    private static String loadPrompt() {
        try (InputStream in = DatingReasonGenerator.class.getClassLoader()
                .getResourceAsStream("prompts/dating-reason-system.txt")) {
            if (in == null) {
                throw new IllegalStateException("dating reason prompt not found");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
