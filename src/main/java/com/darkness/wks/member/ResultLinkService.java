package com.darkness.wks.member;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.member.dto.LinkResultResponse;
import com.darkness.wks.member.entity.Member;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResultLinkService {

    private final EntityManager entityManager;
    private final ResultRepository resultRepository;

    @Transactional
    public LinkResultResponse link(Long memberId, String resultId) {
        UUID id = parseResultId(resultId);
        // 계정당 결과 1개를 보장하고, 같은 계정의 동시 연결을 같은 순서로 직렬화한다.
        if (entityManager.find(Member.class, memberId, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        var owned = resultRepository.findByMemberId(memberId);
        if (owned.isPresent()) {
            return new LinkResultResponse(owned.get().getId());
        }

        Result target = resultRepository.findAllByIdForUpdate(List.of(id)).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND));
        if (target.getMemberId() != null) {
            // 존재 여부를 알려주지 않아 다른 계정 소유의 결과 ID를 탐색할 수 없게 한다.
            throw new BusinessException(ErrorCode.RESULT_NOT_FOUND);
        }
        target.linkMember(memberId);
        resultRepository.flush();
        return new LinkResultResponse(target.getId());
    }

    private UUID parseResultId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (id.version() == 4 && id.toString().equalsIgnoreCase(value)) {
                return id;
            }
        } catch (IllegalArgumentException ignored) {
            // UUID 파싱 실패는 아래에서 동일한 입력 오류로 처리한다.
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
}
