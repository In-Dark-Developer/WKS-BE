package com.darkness.wks.compatibility;

import com.darkness.wks.compatibility.entity.Compatibility;
import com.darkness.wks.result.ResultLinkedEvent;
import com.darkness.wks.wallet.LedgerReason;
import com.darkness.wks.wallet.WalletService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 궁합지도에 친구가 등록되면 공유자(origin)에게 실 +2 (plan.md §1.4·§5.8, 2026-09-29 결정).
 * <p>
 * <b>친구 결과가 로그인 계정에 연결된 경우만</b> 센다. 그 전(2026-09-28~29)에는 친구를 팔자·성별 해시로 구분했는데,
 * 익명 결과는 생년월일·시간·성별을 바꿔 얼마든지 새로 만들 수 있어서 공유 링크 하나로 실이 무한히 쌓였다.
 * 원장 {@code ref_id} 를 친구의 {@code memberId} 로 두면 공유자당 같은 계정은 한 번이고, 계정을 늘리려면
 * 카카오 계정이 필요하다. 공유자 자신(같은 계정)은 주지 않는다. 옛 {@code p:} 해시 행은 잔액 유지를 위해 그대로 둔다.
 * <p>
 * 공유자·친구 어느 쪽이든 로그인 전이면 그때는 못 준다. 나중에 로그인해서 결과가 계정에 연결되면
 * {@link ResultLinkedEvent} 를 받아 그 결과가 낀 궁합을 양쪽 방향으로 훑어 소급 지급한다.
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

    /** 새로 만든 궁합. 두 결과가 모두 계정에 연결돼 있을 때만 지급한다 */
    void rewardNew(Compatibility compatibility) {
        credit(compatibility.getOrigin().getMemberId(), compatibility.getGuest().getMemberId());
    }

    /** 로그인 트랜잭션 안에서 동기로 돈다 — 결과 연결과 소급 지급이 함께 커밋된다 */
    @EventListener
    @Transactional
    public void onResultLinked(ResultLinkedEvent event) {
        // 연결된 쪽은 이벤트의 memberId 를 쓴다 — 같은 트랜잭션에서 막 바뀐 값이라 엔티티에 의존하지 않는다
        for (Compatibility compatibility : compatibilityRepository.findAllByOriginIdWithResults(event.resultId())) {
            credit(event.memberId(), compatibility.getGuest().getMemberId());
        }
        for (Compatibility compatibility : compatibilityRepository.findAllByGuestIdWithResults(event.resultId())) {
            credit(compatibility.getOrigin().getMemberId(), event.memberId());
        }
    }

    private void credit(Long originMemberId, Long guestMemberId) {
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
