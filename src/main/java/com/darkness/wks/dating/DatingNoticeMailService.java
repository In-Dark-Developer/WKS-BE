package com.darkness.wks.dating;

import com.darkness.wks.admin.dto.AdminNoticeMailRequest;
import com.darkness.wks.admin.dto.AdminNoticeMailResponse;
import com.darkness.wks.admin.dto.AdminNoticeMailStatusResponse;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.entity.DatingNoticeMail;
import com.darkness.wks.dating.entity.DatingProfile;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 소개팅 프로필 보유자 전원에게 보내는 일괄 안내 메일 (#159). 운영자가 관리자 API 로 직접 실행한다 —
 * cron 은 실행 시각에 앱이 재기동 중이면 그대로 놓치고, 발송 전 문구를 받아 볼 방법이 없다.
 * <p>
 * SEND 는 대상만 세고 바로 돌려준 뒤 별도 스레드에서 보낸다. 100명 남짓이어도 SMTP 왕복이 쌓이면 CloudFront·nginx
 * 요청 타임아웃을 넘는다. 수신자마다 발송 직전에 DB 로 선점하므로({@link DatingNoticeMailRepository#claim}) 두 번 눌러도,
 * 재실행해도 같은 사람에게 두 번 가지 않는다. 발송 도중 앱이 내려가 SENDING 으로 남은 행은 다시 보내지 않는다 —
 * 실제로 나갔는지 알 수 없고, 같은 메일을 두 번 받는 쪽이 한 통 빠지는 쪽보다 나쁘다.
 * <p>
 * 로그에는 profileId 와 집계만 남긴다 (이메일 금지, AGENTS.md).
 */
@Slf4j
@Service
public class DatingNoticeMailService {

    private final DatingProfileRepository profileRepository;
    private final DatingNoticeMailRepository noticeMailRepository;
    private final JavaMailSender mailSender;
    private final TaskExecutor taskExecutor;
    private final String mailFrom;
    private final long intervalMillis;

    public DatingNoticeMailService(DatingProfileRepository profileRepository,
                                   DatingNoticeMailRepository noticeMailRepository,
                                   JavaMailSender mailSender,
                                   @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                                   @Value("${app.mail.from}") String mailFrom,
                                   // SES 초당 발송 한도보다 충분히 느리게 보낸다. 테스트만 0 으로 낮춘다
                                   @Value("${app.notice-mail.interval-ms:200}") long intervalMillis) {
        this.profileRepository = profileRepository;
        this.noticeMailRepository = noticeMailRepository;
        this.mailSender = mailSender;
        this.taskExecutor = taskExecutor;
        this.mailFrom = mailFrom;
        this.intervalMillis = intervalMillis;
    }

    record Target(UUID profileId, String email) {
    }

    public AdminNoticeMailResponse run(AdminNoticeMailRequest request) {
        if (request.mode() == AdminNoticeMailRequest.Mode.TEST) {
            if (request.testTo() == null || request.testTo().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            try {
                send(request.testTo().trim(), request.subject(), request.body());
            } catch (Exception exception) {
                log.warn("notice mail test failed. campaignKey={}, cause={}", request.campaignKey(),
                        exception.getClass().getSimpleName());
                throw new BusinessException(ErrorCode.MAIL_UNAVAILABLE);
            }
            return new AdminNoticeMailResponse(request.campaignKey(), request.mode(), 1);
        }

        List<Target> targets = targets();
        log.warn("notice mail {}. campaignKey={}, targets={}", request.mode(), request.campaignKey(), targets.size());
        if (request.mode() == AdminNoticeMailRequest.Mode.SEND) {
            taskExecutor.execute(() -> sendAll(request.campaignKey(), request.subject(), request.body(), targets));
        }
        return new AdminNoticeMailResponse(request.campaignKey(), request.mode(), targets.size());
    }

    public AdminNoticeMailStatusResponse status(String campaignKey) {
        Map<DatingNoticeMail.Status, Long> counts = new EnumMap<>(DatingNoticeMail.Status.class);
        for (Object[] row : noticeMailRepository.countByStatus(campaignKey)) {
            counts.put((DatingNoticeMail.Status) row[0], (Long) row[1]);
        }
        return new AdminNoticeMailStatusResponse(campaignKey, targets().size(),
                counts.getOrDefault(DatingNoticeMail.Status.SENT, 0L),
                counts.getOrDefault(DatingNoticeMail.Status.FAILED, 0L),
                counts.getOrDefault(DatingNoticeMail.Status.SENDING, 0L));
    }

    // 엔티티를 트랜잭션 밖으로 들고 나가지 않는다 — 발송 스레드는 id·email 만 쓴다
    private List<Target> targets() {
        return profileRepository.findByDeactivatedAtIsNull().stream()
                .map((DatingProfile profile) -> new Target(profile.getId(), profile.getEmail()))
                .toList();
    }

    void sendAll(String campaignKey, String subject, String body, List<Target> targets) {
        int sent = 0;
        int failed = 0;
        int skipped = 0;
        for (Target target : targets) {
            if (noticeMailRepository.claim(campaignKey, target.profileId()) == 0) {
                skipped++;
                continue;
            }
            try {
                send(target.email(), subject, body);
                noticeMailRepository.mark(campaignKey, target.profileId(), DatingNoticeMail.Status.SENT.name());
                sent++;
            } catch (Exception exception) {
                // 한 사람 실패로 나머지를 멈추지 않는다. 같은 campaignKey 로 다시 SEND 하면 FAILED 만 다시 간다.
                // 예외 메시지에는 수신 주소가 실릴 수 있어 타입만 남긴다
                noticeMailRepository.mark(campaignKey, target.profileId(), DatingNoticeMail.Status.FAILED.name());
                failed++;
                log.warn("notice mail failed. campaignKey={}, profileId={}, cause={}", campaignKey,
                        target.profileId(), exception.getClass().getSimpleName());
            }
            if (!pause()) {
                log.warn("notice mail interrupted. campaignKey={}", campaignKey);
                break;
            }
        }
        log.warn("notice mail done. campaignKey={}, sent={}, failed={}, skipped={}", campaignKey, sent, failed,
                skipped);
    }

    private boolean pause() {
        if (intervalMillis <= 0) {
            return true;
        }
        try {
            Thread.sleep(intervalMillis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void send(String to, String subject, String body) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(mailFrom);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body);
        mailSender.send(message);
    }
}
