package com.darkness.wks.member;

import com.darkness.wks.common.auth.MemberExistence;
import org.springframework.stereotype.Component;

@Component
class MemberExistenceChecker implements MemberExistence {

    private final MemberRepository memberRepository;

    MemberExistenceChecker(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public boolean exists(Long memberId) {
        return memberRepository.existsById(memberId);
    }
}
