package com.darkness.wks.member;

import com.darkness.wks.member.entity.Member;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 카카오 회원 upsert + 브라우저 결과 연결(plan.md §1.1). 카카오 호출 자체는 하지 않는다
 * (auth.AuthService 가 카카오 왕복을 끝낸 뒤 kakaoId 만 넘긴다) — DB 트랜잭션 동안 외부 HTTP 를
 * 붙잡지 않기 위함.
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final ResultRepository resultRepository;
    private final MemberRaceOps raceOps;

    public record LoginResult(Member member, boolean isNewUser, String restoredResultId) {
    }

    @Transactional
    public LoginResult loginAndLink(long kakaoId, String browserResultId) {
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
            } else {
                // 동시에 같은 kakaoId 로 로그인해 경쟁에서 졌다 — 방금 다른 요청이 만든 행을 이 트랜잭션에서 다시 읽는다
                member = memberRepository.findByKakaoId(kakaoId)
                        .orElseThrow(() -> new IllegalStateException(
                                "member upsert race unresolved: kakaoId=" + kakaoId));
                member.touchLogin();
                isNewUser = false;
            }
        }

        // 계정에 이미 저장된 결과가 있으면 그걸 복원한다. 브라우저 결과는 건드리지 않는다(plan §1.1 — 계정 우선).
        Optional<Result> ownedResult = resultRepository.findByMemberId(member.getId());
        if (ownedResult.isPresent()) {
            return new LoginResult(member, isNewUser, ownedResult.get().getId().toString());
        }

        // resultId 가 없거나 형식이 틀리거나(이미 다른 회원 것 포함) 없어도 로그인은 그대로 성공한다(FR-AU-06·07).
        parseUuidV4(browserResultId).ifPresent(id -> raceOps.linkResultIfUnowned(id, member.getId()));
        return new LoginResult(member, isNewUser, null);
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
