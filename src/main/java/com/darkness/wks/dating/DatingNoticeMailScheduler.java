package com.darkness.wks.dating;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 예약된 일괄 안내 메일(#159)을 1분마다 확인한다. 이 앱의 첫 {@code @Scheduled} 라 스케줄링도 여기서 켠다 —
 * 축제 뒤 이 기능을 지우면 함께 사라지게. 다른 작업이 스케줄링을 쓰게 되면 {@code @EnableScheduling} 을 common 설정으로 옮긴다.
 * <p>
 * 앱 인스턴스는 환경마다 하나지만, 둘이 되어도 예약은 조건부 UPDATE 로 한 번만 잡히고 수신자는 행 선점으로 한 번만 간다.
 */
@Slf4j
@Component
@EnableScheduling
class DatingNoticeMailScheduler {

    private final DatingNoticeMailService noticeMailService;

    DatingNoticeMailScheduler(DatingNoticeMailService noticeMailService) {
        this.noticeMailService = noticeMailService;
    }

    @Scheduled(fixedDelayString = "${app.notice-mail.poll-ms:60000}",
            initialDelayString = "${app.notice-mail.poll-ms:60000}")
    void runDueCampaigns() {
        try {
            noticeMailService.runDueCampaigns();
        } catch (Exception exception) {
            // 다음 확인은 그대로 돌아야 한다. 잡힌 예약은 STARTED 로 남으니 같은 키로 SEND 하면 안 간 사람에게만 간다
            log.warn("notice mail scheduler failed. cause={}", exception.getClass().getSimpleName());
        }
    }
}
