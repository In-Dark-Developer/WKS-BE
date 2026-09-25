package com.darkness.wks.member;

import com.darkness.wks.common.Gender;
import com.darkness.wks.member.entity.Member;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final long KAKAO_ID = 111_111_111L;
    private static final UUID RESULT_ID = UUID.fromString("3f2a9c1e-1111-4111-8111-111111111111");

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ResultRepository resultRepository;
    @Mock
    private MemberRaceOps raceOps;
    @Mock
    private WalletService walletService;

    @InjectMocks
    private MemberService memberService;

    private Member memberWithId(long id) {
        Member member = new Member(KAKAO_ID);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Result resultWithId(UUID id) {
        Result result = new Result("도윤", LocalDate.of(2002, 3, 14), null, null,
                Gender.MALE, "갑진", "계묘", "임오", null);
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    @Test
    void 기존_회원이면_로그인_시각만_갱신하고_신규가_아니다() {
        Member existing = memberWithId(1L);
        when(memberRepository.findByKakaoId(KAKAO_ID)).thenReturn(Optional.of(existing));
        when(resultRepository.findByMemberId(1L)).thenReturn(Optional.empty());

        MemberService.LoginResult result = memberService.loginAndLink(KAKAO_ID, null);

        assertThat(result.isNewUser()).isFalse();
        assertThat(result.member()).isSameAs(existing);
        verify(raceOps, never()).createMemberIfAbsent(anyLong());
    }

    @Test
    void 계정에_이미_결과가_있으면_그걸_복원하고_브라우저_결과는_연결하지_않는다() {
        Member existing = memberWithId(1L);
        Result owned = resultWithId(RESULT_ID);
        when(memberRepository.findByKakaoId(KAKAO_ID)).thenReturn(Optional.of(existing));
        when(resultRepository.findByMemberId(1L)).thenReturn(Optional.of(owned));

        MemberService.LoginResult result = memberService.loginAndLink(
                KAKAO_ID, "7b91d26f-2222-4222-8222-222222222222");

        assertThat(result.restoredResultId()).isEqualTo(RESULT_ID.toString());
        verify(raceOps, never()).linkResultIfUnowned(any(), any());
    }

    @Test
    void 계정에_결과가_없고_브라우저_resultId_가_유효하면_연결을_시도한다() {
        Member existing = memberWithId(1L);
        when(memberRepository.findByKakaoId(KAKAO_ID)).thenReturn(Optional.of(existing));
        when(resultRepository.findByMemberId(1L)).thenReturn(Optional.empty());

        MemberService.LoginResult result = memberService.loginAndLink(KAKAO_ID, RESULT_ID.toString());

        assertThat(result.restoredResultId()).isNull();
        verify(raceOps).linkResultIfUnowned(RESULT_ID, 1L);
    }

    @Test
    void 브라우저_resultId_가_null_이거나_형식이_틀리면_연결을_시도하지_않는다() {
        Member existing = memberWithId(1L);
        when(memberRepository.findByKakaoId(KAKAO_ID)).thenReturn(Optional.of(existing));
        when(resultRepository.findByMemberId(1L)).thenReturn(Optional.empty());

        memberService.loginAndLink(KAKAO_ID, null);
        memberService.loginAndLink(KAKAO_ID, "");
        memberService.loginAndLink(KAKAO_ID, "not-a-uuid");
        memberService.loginAndLink(KAKAO_ID, "3f2a9c1e-1111-1111-8111-111111111111"); // v1 형식(4 가 아님)

        verify(raceOps, never()).linkResultIfUnowned(any(), any());
    }

    @Test
    void 신규_회원이면_생성하고_신규_표시를_한다() {
        when(memberRepository.findByKakaoId(KAKAO_ID)).thenReturn(Optional.empty());
        Member created = memberWithId(2L);
        when(raceOps.createMemberIfAbsent(KAKAO_ID)).thenReturn(Optional.of(created));
        when(resultRepository.findByMemberId(2L)).thenReturn(Optional.empty());

        MemberService.LoginResult result = memberService.loginAndLink(KAKAO_ID, null);

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.member()).isSameAs(created);
    }

    @Test
    void 동시_최초_로그인_경쟁에서_지면_다시_조회해_로그인으로_처리한다() {
        Member winnerRow = memberWithId(3L);
        when(memberRepository.findByKakaoId(KAKAO_ID))
                .thenReturn(Optional.empty()) // 첫 조회 — 아직 없다
                .thenReturn(Optional.of(winnerRow)); // 경쟁에서 진 뒤 재조회 — 이미 있다
        when(raceOps.createMemberIfAbsent(KAKAO_ID)).thenReturn(Optional.empty()); // 경쟁에서 짐
        when(resultRepository.findByMemberId(3L)).thenReturn(Optional.empty());

        MemberService.LoginResult result = memberService.loginAndLink(KAKAO_ID, null);

        assertThat(result.isNewUser()).isFalse();
        assertThat(result.member()).isSameAs(winnerRow);
    }

    @Test
    void 경쟁에서_진_뒤_재조회도_실패하면_예외를_던진다() {
        when(memberRepository.findByKakaoId(KAKAO_ID)).thenReturn(Optional.empty());
        when(raceOps.createMemberIfAbsent(KAKAO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.loginAndLink(KAKAO_ID, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
