package com.darkness.wks.member;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.member.dto.MeResponse;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.ResultService;
import com.darkness.wks.result.dto.ResultResponse;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/me, GET /api/me/result. 결과 조회는 {@link ResultService#getResult} 를 그대로 재사용한다
 * (해석·궁합 목록 조립 로직을 여기서 다시 짜지 않는다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeService {

    private final ResultRepository resultRepository;
    private final ResultService resultService;
    private final WalletService walletService;

    public MeResponse getMe(Long memberId) {
        boolean hasResult = resultRepository.existsByMemberId(memberId);
        int threadBalance = walletService.getBalance(memberId);
        return new MeResponse(memberId, hasResult, false, threadBalance);
    }

    public ResultResponse getMyResult(Long memberId) {
        Result result = resultRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND));
        return resultService.getResult(result.getId().toString());
    }
}
