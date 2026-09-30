package com.darkness.wks.member;

import com.darkness.wks.member.entity.Member;
import com.darkness.wks.result.ResultLinkedEvent;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.wallet.LedgerReason;
import com.darkness.wks.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 카카오 회원 upsert + 브라우저 결과 연결(plan.md §1.1). 카카오 호출 자체는 하지 않는다
 * (auth.AuthService 가 카카오 왕복을 끝낸 뒤 kakaoId 만 넘긴다) — DB 트랜잭션 동안 외부 HTTP 를
 * 붙잡지 않기 위함.
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private static final int SIGNUP_BONUS_AMOUNT = 10;
    /** 로그인 요청의 resultIds 상한. 한 브라우저가 로그인 전에 이만큼 넘게 만들 일은 없다 — 요청 하나가 DB 를 오래 잡지 않게 */
    static final int MAX_CLAIMED_RESULTS = 20;

    private final MemberRepository memberRepository;
    private final ResultRepository resultRepository;
    private final MemberRaceOps raceOps;
    private final WalletService walletService;
    private final ApplicationEventPublisher eventPublisher;

    public record LoginResult(Member member, boolean isNewUser, String restoredResultId) {
    }

    @Transactional
    public LoginResult loginAndLink(long kakaoId, String browserResultId) {
        return loginAndLink(kakaoId, browserResultId, List.of());
    }

    /**
     * @param browserResultIds 이 브라우저가 로그인 전에 만든 결과들(최대 {@value #MAX_CLAIMED_RESULTS}개만 본다).
     *                         대표 결과 연결과 별개로, 주인이 없는 것은 이 계정이 만든 것으로 기록해 그 결과로 남긴
     *                         궁합지도 별의 보상을 소급한다(V27). 형식이 틀리거나 없는 id 는 조용히 무시한다
     */
    @Transactional
    public LoginResult loginAndLink(long kakaoId, String browserResultId, List<String> browserResultIds) {
        Member member;
        boolean isNewUser;

        Optional<Member> existing = memberRepository.findByKakaoId(kakaoId);
        if (existing.isPresent()) {
            member = existing.get();
            member.touchLogin();
            isNewUser = false;
        } else {
            Optional<Member> created = raceOps.createMemberIfAbsent(kakaoId);
            if (created.isPresent()) {
                member = created.get();
                isNewUser = true;
                // ref_id = memberId — 계정당 한 번만 지급된다(FR-TH-04). 같은 트랜잭션에서 처리해 회원
                // upsert와 지급이 함께 커밋되거나 함께 롤백된다.
                walletService.credit(member.getId(), LedgerReason.SIGNUP_BONUS,
                        member.getId().toString(), SIGNUP_BONUS_AMOUNT);
            } else {
                // 동시에 같은 kakaoId 로 로그인해 경쟁에서 졌다 — 방금 다른 요청이 만든 행을 이 트랜잭션에서 다시 읽는다
                member = memberRepository.findByKakaoId(kakaoId)
                        .orElseThrow(() -> new IllegalStateException(
                                "member upsert race unresolved: kakaoId=" + kakaoId));
                member.touchLogin();
                isNewUser = false;
            }
        }

        // 계정에 이미 저장된 결과가 있으면 그걸 복원한다. 브라우저 결과는 대표 결과로 연결하지 않는다(plan §1.1 — 계정 우선).
        Optional<Result> ownedResult = resultRepository.findByMemberId(member.getId());
        String restoredResultId = ownedResult.map(result -> result.getId().toString()).orElse(null);
        Set<UUID> settled = new LinkedHashSet<>();
        if (ownedResult.isEmpty()) {
            // resultId 가 없거나 형식이 틀리거나(이미 다른 회원 것 포함) 없어도 로그인은 그대로 성공한다(FR-AU-06·07).
            parseUuidV4(browserResultId)
                    .filter(id -> raceOps.linkResultIfUnowned(id, member.getId()))
                    .ifPresent(settled::add);
        }
        settled.addAll(claimBrowserResults(member.getId(), browserResultId, browserResultIds));
        // 주인이 정해진 결과마다 로그인 전에 쌓인 궁합지도 별의 실을 소급 지급한다 (2026-09-28·30 결정).
        // 동기 리스너라 로그인 트랜잭션에서 함께 커밋된다. 결과 행 잠금(위)을 다 잡은 뒤에 지급해야 궁합 생성과
        // 잠금 순서(결과 → 원장 advisory lock)가 같아 교착이 나지 않는다
        settled.forEach(id -> eventPublisher.publishEvent(new ResultLinkedEvent(id, member.getId())));
        return new LoginResult(member, isNewUser, restoredResultId);
    }

    /**
     * 대표 결과로 연결되지 않은 브라우저 결과도 주인 없는 것은 이 계정이 만든 것으로 기록한다 (2026-09-30).
     * 계정에 결과가 이미 있는 사람이 로그인 전에 새로 만든 결과로 남긴 별이, 로그인 순서 때문에 보상에서 영구히 빠지던
     * 문제를 막는다. 대표 결과 연결(위)이 끝난 뒤 새로 읽어야 방금 연결된 결과를 다시 주인 없음으로 보지 않는다.
     *
     * @return 이번 호출로 주인이 정해진 결과 id
     */
    private List<UUID> claimBrowserResults(Long memberId, String browserResultId, List<String> browserResultIds) {
        Set<UUID> ids = new LinkedHashSet<>();
        parseUuidV4(browserResultId).ifPresent(ids::add);
        if (browserResultIds != null) {
            browserResultIds.stream().limit(MAX_CLAIMED_RESULTS)
                    .map(this::parseUuidV4).flatMap(Optional::stream).forEach(ids::add);
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        // 같은 결과를 두 계정이 동시에 제시하면 먼저 잠근 쪽만 주인이 된다
        return resultRepository.findAllByIdForUpdate(List.copyOf(ids)).stream()
                .filter(result -> result.claimBy(memberId))
                .map(Result::getId)
                .toList();
    }

    private Optional<UUID> parseUuidV4(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value) || id.version() != 4) {
                return Optional.empty();
            }
            return Optional.of(id);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
