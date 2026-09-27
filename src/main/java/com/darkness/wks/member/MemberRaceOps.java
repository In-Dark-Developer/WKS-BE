package com.darkness.wks.member;

import com.darkness.wks.member.entity.Member;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link MemberService} 가 쓰는 동시성 경쟁 처리 두 가지 — 각자 REQUIRES_NEW 로 별도 트랜잭션에 둔다.
 * <p>
 * 같은 클래스 안에서 @Transactional(REQUIRES_NEW) 메서드를 셀프 호출(this.method())하면 Spring 프록시를
 * 거치지 않아 전파 속성이 조용히 무시된다. 그래서 별도 빈으로 뺐다 — {@link MemberService} 가 이 빈을
 * 통해서만 부른다. 실패(동시 경쟁으로 인한 unique 위반)해도 이 메서드의 트랜잭션만 롤백되고,
 * 호출한 쪽의 트랜잭션(퍼시스턴스 컨텍스트)은 영향받지 않는다 — 같은 트랜잭션에서 잡았다면
 * 그 세션이 이후 사용에 안전하다고 보장할 수 없다.
 */
@Component
class MemberRaceOps {

    private final MemberRepository memberRepository;
    private final ResultRepository resultRepository;

    MemberRaceOps(MemberRepository memberRepository, ResultRepository resultRepository) {
        this.memberRepository = memberRepository;
        this.resultRepository = resultRepository;
    }

    /** 동시에 같은 kakaoId 로 최초 로그인하면 하나만 성공한다. 진 쪽은 empty — 호출자가 다시 조회한다 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Optional<Member> createMemberIfAbsent(long kakaoId) {
        try {
            return Optional.of(memberRepository.saveAndFlush(new Member(kakaoId)));
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();
        }
    }

    /** 결과가 이미 다른 회원 것이면(동시에 채감) 조용히 실패로 본다 — 로그인 자체는 그대로 성공이다 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void linkResultIfUnowned(UUID resultId, Long memberId) {
        Optional<Result> target = resultRepository.findByIdAndMemberIdIsNull(resultId);
        if (target.isEmpty()) {
            return;
        }
        Result result = target.get();
        result.linkMember(memberId);
        try {
            resultRepository.saveAndFlush(result);
        } catch (DataIntegrityViolationException e) {
            // 이 트랜잭션만 롤백된다 — 연결이 안 됐을 뿐 로그인은 그대로 성공
        }
    }
}
