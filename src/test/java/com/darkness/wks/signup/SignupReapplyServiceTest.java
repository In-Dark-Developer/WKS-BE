package com.darkness.wks.signup;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.Gender;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.signup.dto.SignupReapplyResponse;
import com.darkness.wks.signup.entity.Signup;
import com.darkness.wks.signup.entity.SignupReapplyInvite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupReapplyServiceTest {

    @Mock
    private SignupRepository signupRepository;
    @Mock
    private SignupReapplyInviteRepository inviteRepository;
    @Mock
    private JavaMailSender mailSender;

    private SignupReapplyService service() {
        SignupReapplyService service = new SignupReapplyService(signupRepository, inviteRepository, mailSender);
        ReflectionTestUtils.setField(service, "ttlHours", 48L);
        ReflectionTestUtils.setField(service, "campaignDomain", "dgu.ac.kr");
        ReflectionTestUtils.setField(service, "mailFrom", "noreply@wks.local");
        ReflectionTestUtils.setField(service, "reapplyUrl", "http://localhost:3000/dating/reapply");
        return service;
    }

    private Signup signup(String email, Result result) {
        Signup signup = new Signup(email, result, Gender.MALE, Gender.FEMALE, "김동국", ContactMethod.PHONE,
                "010-1234-5678", "컴퓨터공학과", "INFP", "자기소개", null);
        ReflectionTestUtils.setField(signup, "id", 7L);
        return signup;
    }

    private Result result() {
        Result result = new Result("동국", LocalDate.of(2002, 3, 14), null, null, Gender.MALE,
                "임오", "계묘", "갑진", "신미");
        ReflectionTestUtils.setField(result, "id", UUID.randomUUID());
        return result;
    }

    @Test
    void prefillReturnsSignupFieldsAndResultIdWithoutConsumingToken() {
        Result result = result();
        SignupReapplyInvite invite = new SignupReapplyInvite("token", signup("dev@dgu.ac.kr", result),
                Instant.now().plusSeconds(3600));
        when(inviteRepository.findById("token")).thenReturn(Optional.of(invite));

        SignupReapplyResponse response = service().prefill("token");

        assertThat(response.email()).isEqualTo("dev@dgu.ac.kr");
        assertThat(response.resultId()).isEqualTo(result.getId());
        assertThat(response.department()).isEqualTo("컴퓨터공학과");
        assertThat(invite.getUsedAt()).isNull();
    }

    @Test
    void invitedEmailIsLowercased() {
        SignupReapplyInvite invite = new SignupReapplyInvite("token", signup("DEV@dgu.ac.kr", result()),
                Instant.now().plusSeconds(3600));
        when(inviteRepository.findById("token")).thenReturn(Optional.of(invite));

        assertThat(service().invitedEmail("token")).isEqualTo("dev@dgu.ac.kr");
    }

    @Test
    void consumeInviteMarksTokenUsed() {
        SignupReapplyInvite invite = new SignupReapplyInvite("token", signup("dev@dgu.ac.kr", result()),
                Instant.now().plusSeconds(3600));
        when(inviteRepository.findById("token")).thenReturn(Optional.of(invite));

        service().consumeInvite("token");

        assertThat(invite.getUsedAt()).isNotNull();
    }

    @Test
    void expiredOrUsedOrUnknownTokenIsRejected() {
        SignupReapplyInvite expired = new SignupReapplyInvite("expired", signup("dev@dgu.ac.kr", result()),
                Instant.now().minusSeconds(1));
        SignupReapplyInvite used = new SignupReapplyInvite("used", signup("dev@dgu.ac.kr", result()),
                Instant.now().plusSeconds(3600));
        used.markUsed(Instant.now());
        when(inviteRepository.findById("expired")).thenReturn(Optional.of(expired));
        when(inviteRepository.findById("used")).thenReturn(Optional.of(used));
        when(inviteRepository.findById("nope")).thenReturn(Optional.empty());
        SignupReapplyService service = service();

        for (String token : List.of("expired", "used", "nope")) {
            assertThatThrownBy(() -> service.prefill(token))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
        }
    }

    @Test
    void targetsExcludeLookalikeDomains() {
        when(signupRepository.findReapplyTargets(eq("dgu.ac.kr"), any())).thenReturn(List.of(
                signup("real@dgu.ac.kr", result()),
                signup("sub@cs.dgu.ac.kr", result()),
                signup("fake@notdgu.ac.kr", result())));

        assertThat(service().findTargets())
                .extracting(SignupReapplyService.ReapplyTarget::email)
                .containsExactly("real@dgu.ac.kr", "sub@cs.dgu.ac.kr");
    }

    @Test
    void issueInviteUsesConfiguredTtl() {
        Signup signup = signup("dev@dgu.ac.kr", result());
        when(signupRepository.getReferenceById(7L)).thenReturn(signup);
        when(inviteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SignupReapplyInvite invite = service().issueInvite(7L);

        assertThat(invite.getToken()).isNotBlank();
        assertThat(invite.getExpiresAt()).isBetween(Instant.now().plusSeconds(47 * 3600),
                Instant.now().plusSeconds(49 * 3600));
    }
}
