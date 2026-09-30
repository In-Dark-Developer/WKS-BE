package com.darkness.wks.common.auth;

/**
 * 토큰의 memberId 가 아직 회원 행으로 남아 있는지. 서명·만료만 보면 DB 에서 지워진 회원의 JWT 도 통과해, 조회는 잔액 0 으로
 * 200 이고 원장 쓰기는 FK 위반 500 이 났다(2026-09-30 dev). common 이 member 를 참조하지 않게 인터페이스만 두고 구현은
 * member 패키지에 둔다.
 */
public interface MemberExistence {

    boolean exists(Long memberId);
}
