package com.darkness.wks.compatibility;

import com.darkness.wks.compatibility.entity.Compatibility;
import com.darkness.wks.result.ResultLinkedEvent;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.wallet.LedgerReason;
import com.darkness.wks.wallet.WalletService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 궁합지도에 친구가 등록되면 공유자(origin)에게 실 +2 (plan.md §1.4·§5.8, 2026-09-29 결정).
 * <p>
 * <b>친구 결과의 주인 계정이 확인된 경우만</b> 센다. 그 전(2026-09-28~29)에는 친구를 팔자·성별 해시로 구분했는데,
 * 익명 결과는 생년월일·시간·성별을 바꿔 얼마든지 새로 만들 수 있어서 공유 링크 하나로 실이 무한히 쌓였다.
 * 원장 {@code ref_id} 를 친구의 {@code memberId} 로 두면 공유자당 같은 계정은 한 번이고, 계정을 늘리려면
 * 카카오 계정이 필요하다. 공유자 자신(같은 계정)은 주지 않는다. 옛 {@code p:} 해시 행은 잔액 유지를 위해 그대로 둔다.
 * <p>
 * 주인 계정은 {@link Result#getOwnerMemberId()} 다 — 계정 대표 결과({@code member_id})뿐 아니라 로그인한 계정이
 * 만들었거나 로그인하며 제시한 결과({@code claimed_member_id}, V27)도 센다 (2026-09-30). 전에는 대표 결과만 봐서,
 * 계정에 결과가 이미 있는 사람이 새로 만든 결과로 남긴 별은 로그인 순서에 따라 영구히 빠졌다. 궁합을 만들 때
 * ({@link #rewardNew})와 결과의 주인이 정해질 때({@link #rewardAllOf}) 같은 규칙을 평가하고, 중복은 원장 UNIQUE 가 막는다.
 */
@Service
public class MapFriendRewardService {

    static final int AMOUNT = 2;

    private final CompatibilityRepository compatibilityRepository;
    private final WalletService walletService;

    public MapFriendRewardService(CompatibilityRepository compatibilityRepository, WalletService walletService) {
        this.compatibilityRepository = compatibilityRepository;
        this.walletService = walletService;
    }

    /** 새로 만든 궁합. 두 결과의 주인 계정이 모두 있을 때만 지급한다 */
    void rewardNew(Compatibility compatibility) {
        credit(compatibility);
    }

    /** 로그인 트랜잭션 안에서 동기로 돈다 — 결과의 주인 확정과 소급 지급이 함께 커밋된다 */
    @EventListener
    @Transactional
    public void onResultLinked(ResultLinkedEvent event) {
        rewardAllOf(event.resultId());
    }

    /** 이 결과가 낀 궁합을 양쪽 방향으로 훑어 지급한다. 결과의 주인이 방금 정해졌을 때 부른다 */
    @Transactional
    public void rewardAllOf(UUID resultId) {
        compatibilityRepository.findAllByOriginIdWithResults(resultId).forEach(this::credit);
        compatibilityRepository.findAllByGuestIdWithResults(resultId).forEach(this::credit);
    }

    private void credit(Compatibility compatibility) {
        Long originMemberId = compatibility.getOrigin().getOwnerMemberId();
        Long guestMemberId = compatibility.getGuest().getOwnerMemberId();
        if (originMemberId == null || guestMemberId == null || originMemberId.equals(guestMemberId)) {
            return;
        }
        walletService.credit(originMemberId, LedgerReason.MAP_FRIEND, refId(guestMemberId), AMOUNT);
    }

    /** 원장 ref_id. 옛 팔자 해시 행({@code p:}+hex)과 구분되게 접두어를 둔다 */
    static String refId(Long guestMemberId) {
        return "m:" + guestMemberId;
    }
}
