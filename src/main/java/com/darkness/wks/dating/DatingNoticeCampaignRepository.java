package com.darkness.wks.dating;

import com.darkness.wks.dating.entity.DatingNoticeCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 시각 비교는 전부 DB 의 now() 로 한다 — 앱 인스턴스·컨테이너 시간대와 무관하게 send_at(TIMESTAMPTZ) 과 맞춘다.
 */
public interface DatingNoticeCampaignRepository extends JpaRepository<DatingNoticeCampaign, String> {

    /** 예약을 만들거나, 아직 시작 전이면 고친다. 이미 시작·만료됐으면 0 */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO dating_notice_campaign (campaign_key, subject, body, send_at, state)
            VALUES (:campaignKey, :subject, :body, :sendAt, 'SCHEDULED')
            ON CONFLICT (campaign_key) DO UPDATE
                SET subject = EXCLUDED.subject, body = EXCLUDED.body, send_at = EXCLUDED.send_at
                WHERE dating_notice_campaign.state = 'SCHEDULED'
            """, nativeQuery = true)
    int schedule(@Param("campaignKey") String campaignKey, @Param("subject") String subject,
                 @Param("body") String body, @Param("sendAt") Instant sendAt);

    /** 시작 전 예약만 지운다. 지웠으면 1 */
    @Transactional
    @Modifying
    @Query(value = "DELETE FROM dating_notice_campaign WHERE campaign_key = :campaignKey AND state = 'SCHEDULED'",
            nativeQuery = true)
    int cancel(@Param("campaignKey") String campaignKey);

    @Query(value = "SELECT campaign_key FROM dating_notice_campaign WHERE state = 'SCHEDULED' AND send_at <= now()",
            nativeQuery = true)
    List<String> findDueKeys();

    /**
     * 시각이 지난 예약을 이 호출이 잡는다. 늦은 정도가 허용 범위면 STARTED, 넘었으면 EXPIRED 로 바꾸고 1 을 돌려준다.
     * 이미 다른 쪽이 잡았으면 0. 바뀐 상태는 호출 쪽이 다시 읽는다
     */
    @Transactional
    @Modifying
    @Query(value = """
            UPDATE dating_notice_campaign
            SET state = CASE WHEN send_at >= now() - make_interval(mins => :maxDelayMinutes)
                             THEN 'STARTED' ELSE 'EXPIRED' END,
                started_at = now()
            WHERE campaign_key = :campaignKey AND state = 'SCHEDULED' AND send_at <= now()
            """, nativeQuery = true)
    int claim(@Param("campaignKey") String campaignKey, @Param("maxDelayMinutes") int maxDelayMinutes);
}
