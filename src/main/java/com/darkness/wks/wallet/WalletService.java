package com.darkness.wks.wallet;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.wallet.entity.ThreadLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실(재화) 원장 하나로 지급·차감·잔액을 전부 다룬다 (plan.md §9.4). 다른 도메인을 참조하지 않는다
 * (architecture.md §3 — wallet 이 가장 아래) — 호출부(compatibility·dating·member)가 memberId 만
 * 넘긴다. 동시성은 {@link ThreadLedgerRepository#lockMember} 로 회원 단위 직렬화하고, 중복 지급·차감은
 * {@code UNIQUE(member_id, reason, ref_id)} 로 막는다 (plan.md §9.5, FR-TH-02).
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final ThreadLedgerRepository ledgerRepository;

    @Transactional(readOnly = true)
    public int getBalance(Long memberId) {
        return ledgerRepository.sumAmountByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public boolean hasCredited(LedgerReason reason, Long memberId, String refId) {
        return ledgerRepository.existsByMemberIdAndReasonAndRefId(memberId, reason, refId);
    }

    /**
     * 같은 사유로 {@code ref_id} 가 접두어로 시작하는 원장 행 수. 리롤처럼 하루에 여러 번 일어나는
     * 사유의 "오늘 몇 번째인가"를 세는 데 쓴다 — 무료분도 {@code amount = 0} 행으로 남기므로 센다.
     */
    @Transactional(readOnly = true)
    public int countEntries(Long memberId, LedgerReason reason, String refIdPrefix) {
        return (int) ledgerRepository.countByMemberIdAndReasonAndRefIdStartingWith(memberId, reason, refIdPrefix);
    }

    /**
     * 실을 지급한다. {@code ref_id} 가 이미 쓰였으면(같은 사유로 이미 지급됨) 아무 일도 하지 않는다 —
     * 호출부가 매번 조건 없이 불러도 안전하다(가입 보너스·출석·친구 등록 전부 이 방식).
     *
     * @return 이번 호출로 실제 지급됐으면 true, 이미 지급된 상태라 아무 일도 안 했으면 false
     */
    @Transactional
    public boolean credit(Long memberId, LedgerReason reason, String refId, int amount) {
        ledgerRepository.lockMember(memberId);
        if (ledgerRepository.existsByMemberIdAndReasonAndRefId(memberId, reason, refId)) {
            return false;
        }
        ledgerRepository.save(new ThreadLedger(memberId, amount, reason, refId));
        return true;
    }

    /**
     * 실을 차감한다. 잔액이 모자라면 {@link ErrorCode#INSUFFICIENT_THREAD}. {@code ref_id} 가 이미
     * 쓰였으면(이미 차감됨) 다시 차감하지 않고 조용히 반환한다 — 호출부(해금)는 그 전에 이미 해금
     * 여부를 플래그로 확인하지만, 여기서도 한 번 더 막는다(방어적 이중 확인).
     */
    @Transactional
    public void debit(Long memberId, LedgerReason reason, String refId, int cost) {
        ledgerRepository.lockMember(memberId);
        if (ledgerRepository.existsByMemberIdAndReasonAndRefId(memberId, reason, refId)) {
            return;
        }
        int balance = ledgerRepository.sumAmountByMemberId(memberId);
        if (balance < cost) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_THREAD);
        }
        ledgerRepository.save(new ThreadLedger(memberId, -cost, reason, refId));
    }
}
