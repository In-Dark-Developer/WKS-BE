package com.darkness.wks.signup;

import com.darkness.wks.signup.entity.SignupReapplyInvite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 재신청 초대 일괄 발송. 공개 API 로 두지 않는다 — 관리자 인증이 없는데 대량 메일 발송 트리거를
 * 열어두면 안 된다. 기동 시 {@code app.signup.reapply-campaign.mode} 를 보고 한 번 돌고 끝난다.
 * <p>
 * <b>기본값은 반드시 {@code off} 다.</b> {@code dev} push 가 곧 배포라 값이 켜진 채로 남으면 재기동마다
 * 다시 돈다. 이미 완료됐거나 아직 유효한 초대는 대상에서 빠지므로(같은 사람에게 두 번 가지 않는다)
 * 피해는 "48시간이 지나도 안 누른 사람에게 재발송" 정도지만, 쓰고 나면 값을 돌려놓는 게 맞다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SignupReapplyCampaignRunner implements ApplicationRunner {

    private static final String DRY_RUN = "dry-run";
    private static final String SEND = "send";

    private final SignupReapplyService reapplyService;

    @Value("${app.signup.reapply-campaign.mode}")
    private String mode;

    @Override
    public void run(ApplicationArguments args) {
        boolean dryRun = DRY_RUN.equalsIgnoreCase(mode);
        if (!dryRun && !SEND.equalsIgnoreCase(mode)) {
            return;
        }

        List<SignupReapplyService.ReapplyTarget> targets = reapplyService.findTargets();
        log.warn("reapply campaign start. mode={}, targets={}", mode, targets.size());
        if (dryRun) {
            return;
        }

        int sent = 0;
        int failed = 0;
        for (SignupReapplyService.ReapplyTarget target : targets) {
            SignupReapplyInvite invite = reapplyService.issueInvite(target.signupId());
            try {
                reapplyService.sendInviteEmail(target.email(), invite.getToken());
                sent++;
            } catch (SignupReapplyService.InviteMailFailedException exception) {
                reapplyService.discardInvite(invite.getToken());
                failed++;
                // 이메일·토큰은 로그에 남기지 않는다 (AGENTS.md). 원인은 예외 타입까지만
                log.warn("reapply invite mail failed. signupId={}, cause={}", target.signupId(),
                        exception.getCause().getClass().getSimpleName());
            }
        }
        log.warn("reapply campaign done. sent={}, failed={}", sent, failed);
    }
}
