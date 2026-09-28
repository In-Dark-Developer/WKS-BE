package com.darkness.wks.compatibility;

import com.darkness.wks.compatibility.entity.Compatibility;
import com.darkness.wks.result.ResultLinkedEvent;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.wallet.LedgerReason;
import com.darkness.wks.wallet.WalletService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 궁합지도에 서로 다른 친구가 5명 등록될 때마다 공유자(origin)에게 실 +3 (plan.md §1.4·§5.8, 2026-09-29 "1명당 3" 에서 변경).
 * 친구 한 명마다 원장에 한 줄을 남기고 5번째·10번째… 줄만 3, 나머지는 0 이다({@link WalletService#creditEveryNth}).
 * <p>
 * <b>같은 사람은 한 번만</b> 센다(2026-09-28 결정). 익명 결과는 얼마든지 새로 만들 수 있어서, 궁합 id 로만
 * 막으면 같은 생년월일로 결과를 반복 생성해 내 링크에 등록하는 것만으로 실이 무한히 쌓였다. 그래서 원장
 * {@code ref_id} 를 궁합 id 가 아니라 <b>친구의 팔자·성별로 만든 해시</b>로 둔다 — 원장 UNIQUE 가 공유자별로 같은
 * 사람을 두 번 세지 않게 막는다. 시각을 분 단위로 바꿔도 같은 시주면 같은 사람으로 본다. 원문 팔자는 사실상 생년월일이라
 * 원장에 남기지 않으려고 해시로 둔다. 공유자 자신과 같은 팔자·성별인 친구(내 결과를 새로 만들어 등록)는 주지 않는다.
 * <p>
 * 공유자 결과가 로그인 전이라 계정이 없으면 그때는 못 준다. 나중에 로그인해서 결과가 계정에 연결되면
 * {@link ResultLinkedEvent} 를 받아 그동안 등록된 친구 몫을 같은 규칙으로 소급 지급한다(2026-09-28 결정).
 */
@Service
public class MapFriendRewardService {

    static final int FRIENDS_PER_REWARD = 5;
    static final int AMOUNT = 3;

    private final CompatibilityRepository compatibilityRepository;
    private final WalletService walletService;

    public MapFriendRewardService(CompatibilityRepository compatibilityRepository, WalletService walletService) {
        this.compatibilityRepository = compatibilityRepository;
        this.walletService = walletService;
    }

    /** 새로 만든 궁합. 공유자가 계정에 연결돼 있을 때만 지급한다 */
    void rewardNew(Compatibility compatibility) {
        Result origin = compatibility.getOrigin();
        if (origin.getMemberId() != null) {
            credit(origin.getMemberId(), origin, compatibility.getGuest());
        }
    }

    /** 로그인 트랜잭션 안에서 동기로 돈다 — 결과 연결과 소급 지급이 함께 커밋된다 */
    @EventListener
    @Transactional
    public void onResultLinked(ResultLinkedEvent event) {
        for (Compatibility compatibility : compatibilityRepository.findAllByOriginIdWithResults(event.resultId())) {
            credit(event.memberId(), compatibility.getOrigin(), compatibility.getGuest());
        }
    }

    private void credit(Long memberId, Result origin, Result guest) {
        String guestKey = personKey(guest);
        if (guestKey.equals(personKey(origin))) {
            return;
        }
        walletService.creditEveryNth(memberId, LedgerReason.MAP_FRIEND, guestKey, FRIENDS_PER_REWARD, AMOUNT);
    }

    /** 팔자 네 기둥 + 성별. 같은 입력이면 같은 사람으로 본다. 원장 ref_id(VARCHAR 100)에 들어가는 32자 hex */
    static String personKey(Result result) {
        String source = String.join("|", result.getYearPillar(), result.getMonthPillar(), result.getDayPillar(),
                Objects.toString(result.getHourPillar(), "-"), result.getGender().name());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return "p:" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
