package com.darkness.wks.result;

import java.util.UUID;

/**
 * 익명으로 만든 사주 결과가 로그인으로 계정에 연결됐다. 연결 전에 쌓인 친구 궁합의 실 보상을 소급 지급하는 데 쓴다
 * (compatibility 가 받는다). member 가 compatibility 를 직접 부르면 architecture.md 에 없는 의존이 생겨서,
 * 양쪽이 이미 의존하는 result 에 이벤트를 둔다.
 */
public record ResultLinkedEvent(UUID resultId, Long memberId) {
}
