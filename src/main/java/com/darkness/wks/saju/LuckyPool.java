package com.darkness.wks.saju;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/** {@code 오행=항목|항목|...} 형식 리소스 로더. 오행별 최소 1개 없으면 기동 실패 */
final class LuckyPool {

    private LuckyPool() {
    }

    /**
     * 키 → 풀 인덱스. {@code String.hashCode()} 는 끝 글자에 선형이라 날짜가 하루 지나면 해시도 1씩 늘어
     * 풀을 목록 순서대로 도는 주기가 생긴다. SplittableRandom 의 시드 섞기로 흩뜨린다 (결정적, JVM 무관)
     */
    static <T> T pick(List<T> pool, String key) {
        return pool.get(new SplittableRandom(key.hashCode()).nextInt(pool.size()));
    }

    static Map<Element, List<String>> load(String path) {
        Map<Element, List<String>> map = new EnumMap<>(Element.class);
        try (InputStream in = LuckyPool.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("resource not found: " + path);
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] kv = line.split("=", 2);
                map.put(Element.valueOf(kv[0].strip()), List.of(kv[1].split("\\|")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Element e : Element.values()) {
            if (map.getOrDefault(e, List.of()).isEmpty()) throw new IllegalStateException(path + " 에 " + e + " 항목이 없다");
        }
        return map;
    }
}
