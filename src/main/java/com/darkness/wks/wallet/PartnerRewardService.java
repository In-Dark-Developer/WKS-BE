package com.darkness.wks.wallet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 제휴처 배너 유입 보상 (plan.md §1.4 "제휴처 배너 유입"). 제휴처 링크의 {@code ref} 코드로 로그인하거나,
 * 이미 로그인한 사람이 그 링크로 들어오면 계정당 코드별 1회 지급한다(2026-09-28 결정, 축제 사이트 {@code FESTIVAL} 10실).
 * <p>
 * 코드 목록은 설정({@code app.partner.rewards}, "코드:금액:표시이름" 을 콤마로 나열)으로 둔다 — 제휴처가 늘어도
 * 배포 없이 .env 만 바꾼다. 중복 지급은 원장 {@code UNIQUE(member_id, reason, ref_id)} 가 막는다(ref_id = 코드).
 * 모르는 코드는 조용히 무시한다 — {@code ref} 가 잘못돼도 로그인은 성공해야 한다(plan.md §8.1).
 */
@Slf4j
@Service
public class PartnerRewardService {

    private final WalletService walletService;
    private final Map<String, Partner> partners;

    public record Partner(String code, int amount, String name) {
    }

    /** 이번 호출로 실제 지급됐을 때만 돌려준다 */
    public record Granted(String partnerName, int amount) {
    }

    public PartnerRewardService(WalletService walletService, @Value("${app.partner.rewards:}") String raw) {
        this.walletService = walletService;
        this.partners = parse(raw);
    }

    /** 등록된 코드이고 이 계정이 아직 안 받았으면 지급한다. 모르는 코드·이미 받은 코드면 empty */
    public Optional<Granted> grant(Long memberId, String ref) {
        if (ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        Partner partner = partners.get(ref.trim().toUpperCase(Locale.ROOT));
        if (partner == null) {
            return Optional.empty();
        }
        boolean credited = walletService.credit(memberId, LedgerReason.PARTNER, partner.code(), partner.amount());
        return credited ? Optional.of(new Granted(partner.name(), partner.amount())) : Optional.empty();
    }

    /**
     * 형식이 틀린 항목은 건너뛰고 로그만 남긴다. 기동을 막으면 .env 오타 하나로 축제 중 서비스 전체가 죽는다 —
     * 보상 하나가 빠지는 편이 낫다.
     */
    static Map<String, Partner> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(PartnerRewardService::parseEntry)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableMap(Partner::code, partner -> partner, (first, second) -> first));
    }

    private static Optional<Partner> parseEntry(String entry) {
        String[] parts = entry.split(":", 3);
        try {
            String code = parts[0].trim().toUpperCase(Locale.ROOT);
            int amount = Integer.parseInt(parts[1].trim());
            String name = parts[2].trim();
            // ref_id 컬럼이 VARCHAR(100) 이고, 0 이하 금액은 "보상"이 아니다
            if (code.isEmpty() || code.length() > 100 || amount <= 0 || name.isEmpty()) {
                throw new IllegalArgumentException();
            }
            return Optional.of(new Partner(code, amount, name));
        } catch (RuntimeException exception) {
            log.error("invalid app.partner.rewards entry skipped: {}", entry);
            return Optional.empty();
        }
    }
}
