package com.darkness.wks.saju;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * {@code 오행=항목|항목|...} 형식 리소스 로더. 오행별 최소 1개 없으면 예외.
 * static 초기화에서 불리므로 잘못된 리소스는 기동이 아니라 그 클래스를 처음 쓰는 요청에서 터진다. 테스트가 로드해 먼저 잡는다
 */
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
        for (Map.Entry<String, String> kv : readKeyValues(path).entrySet()) {
            try {
                map.put(Element.valueOf(kv.getKey()), List.of(kv.getValue().split("\\|")));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(path + " 의 오행 키가 잘못됐다: " + kv.getKey(), e);
            }
        }
        for (Element e : Element.values()) {
            if (map.getOrDefault(e, List.of()).isEmpty()) throw new IllegalStateException(path + " 에 " + e + " 항목이 없다");
        }
        return map;
    }

    /** {@code 키=값} 줄을 읽는다. 빈 줄·{@code #} 주석은 건너뛰고, {@code =} 가 없는 줄은 파일·줄 번호와 함께 예외 */
    static Map<String, String> readKeyValues(String path) {
        Map<String, String> map = new LinkedHashMap<>();
        try (InputStream in = LuckyPool.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("resource not found: " + path);
            String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] kv = line.split("=", 2);
                if (kv.length != 2) throw new IllegalStateException(path + ":" + (i + 1) + " 에 '=' 가 없다: " + line);
                map.put(kv[0].strip(), kv[1].strip());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return map;
    }
}
