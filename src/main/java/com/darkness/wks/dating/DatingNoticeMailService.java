package com.darkness.wks.dating;

import com.darkness.wks.admin.dto.AdminNoticeMailRequest;
import com.darkness.wks.admin.dto.AdminNoticeMailResponse;
import com.darkness.wks.admin.dto.AdminNoticeMailStatusResponse;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.entity.DatingNoticeCampaign;
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

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 소개팅 프로필 보유자 전원에게 보내는 일괄 안내 메일 (#159). 관리자 API 로 지금 보내거나(SEND) 시각을 예약한다(SCHEDULE).
 * 예약은 고정 cron 이 아니라 DB 에 두고 1분마다 확인한다 — cron 은 그 시각에 앱이 재기동 중이면 그대로 놓치지만,
 * 이 방식은 뜬 뒤 다음 확인에서 보낸다. 문구도 코드가 아니라 예약에 들어 있어 재배포 없이 고친다.
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

    // 예약 시각에서 이만큼 넘게 늦었으면 보내지 않는다. 10/2 오전 9~10시 창에 맞춘 값이다
    static final int MAX_DELAY_MINUTES = 60;

    private final DatingProfileRepository profileRepository;
    private final DatingNoticeMailRepository noticeMailRepository;
    private final DatingNoticeCampaignRepository campaignRepository;
    private final JavaMailSender mailSender;
    private final TaskExecutor taskExecutor;
    private final String mailFrom;
    private final long intervalMillis;

    public DatingNoticeMailService(DatingProfileRepository profileRepository,
                                   DatingNoticeMailRepository noticeMailRepository,
                                   DatingNoticeCampaignRepository campaignRepository,
                                   JavaMailSender mailSender,
                                   @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                                   @Value("${app.mail.from}") String mailFrom,
                                   // SES 초당 발송 한도보다 충분히 느리게 보낸다. 테스트만 0 으로 낮춘다
                                   @Value("${app.notice-mail.interval-ms:200}") long intervalMillis) {
        this.profileRepository = profileRepository;
        this.noticeMailRepository = noticeMailRepository;
        this.campaignRepository = campaignRepository;
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

        if (request.mode() == AdminNoticeMailRequest.Mode.SCHEDULE) {
            schedule(request);
        }

        List<Target> targets = targets();
        log.warn("notice mail {}. campaignKey={}, targets={}", request.mode(), request.campaignKey(), targets.size());
        if (request.mode() == AdminNoticeMailRequest.Mode.SEND) {
            taskExecutor.execute(() -> sendAll(request.campaignKey(), request.subject(), request.body(), targets));
        }
        // SCHEDULE 의 targets 는 지금 기준이다. 실제 대상은 발송 시각에 다시 센다
        return new AdminNoticeMailResponse(request.campaignKey(), request.mode(), targets.size());
    }

    private void schedule(AdminNoticeMailRequest request) {
        // 과거 시각을 받으면 다음 확인에서 바로 나가 버린다 — 즉시 발송은 SEND 로 하게 막는다
        if (request.sendAt() == null || !request.sendAt().toInstant().isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        // 이미 시작·만료된 예약은 고칠 수 없다. 같은 사람에게 다시 보내려면 새 키로
        if (campaignRepository.schedule(request.campaignKey(), request.subject(), request.body(),
                request.sendAt().toInstant()) == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    /** 시작 전 예약을 지운다. 없거나 이미 시작했으면 400 — 관리자 전용이라 ErrorCode 를 새로 만들지 않았다 */
    public void cancelSchedule(String campaignKey) {
        if (campaignRepository.cancel(campaignKey) == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        log.warn("notice mail schedule cancelled. campaignKey={}", campaignKey);
    }

    /**
     * 시각이 지난 예약을 보낸다. {@link DatingNoticeMailScheduler} 가 1분마다 부른다. 스케줄러 스레드에서 끝까지
     * 보낸다 — 예약은 하나뿐이고, 다른 스레드로 넘기면 확인 주기와 발송이 겹칠 이유만 생긴다.
     */
    public void runDueCampaigns() {
        for (String campaignKey : campaignRepository.findDueKeys()) {
            if (campaignRepository.claim(campaignKey, MAX_DELAY_MINUTES) == 0) {
                continue;
            }
            DatingNoticeCampaign campaign = campaignRepository.findById(campaignKey).orElseThrow();
            if (campaign.getState() == DatingNoticeCampaign.State.EXPIRED) {
                // 앱이 오래 내려가 있었던 경우. 늦게라도 보내는 것보다 안 보내는 게 낫다 — 필요하면 사람이 SEND
                log.warn("notice mail schedule expired. campaignKey={}, sendAt={}", campaignKey, campaign.getSendAt());
                continue;
            }
            List<Target> targets = targets();
            log.warn("notice mail scheduled send. campaignKey={}, targets={}", campaignKey, targets.size());
            sendAll(campaignKey, campaign.getSubject(), campaign.getBody(), targets);
        }
    }

    public AdminNoticeMailStatusResponse status(String campaignKey) {
        Map<DatingNoticeMail.Status, Long> counts = new EnumMap<>(DatingNoticeMail.Status.class);
        for (Object[] row : noticeMailRepository.countByStatus(campaignKey)) {
            counts.put((DatingNoticeMail.Status) row[0], (Long) row[1]);
        }
        DatingNoticeCampaign campaign = campaignRepository.findById(campaignKey).orElse(null);
        return new AdminNoticeMailStatusResponse(campaignKey, targets().size(),
                counts.getOrDefault(DatingNoticeMail.Status.SENT, 0L),
                counts.getOrDefault(DatingNoticeMail.Status.FAILED, 0L),
                counts.getOrDefault(DatingNoticeMail.Status.SENDING, 0L),
                campaign == null ? null : campaign.getState().name(),
                campaign == null ? null : campaign.getSendAt(),
                campaign == null ? null : campaign.getStartedAt());
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
