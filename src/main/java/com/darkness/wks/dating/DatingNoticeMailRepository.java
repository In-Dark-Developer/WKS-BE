package com.darkness.wks.dating;

import com.darkness.wks.dating.entity.DatingNoticeMail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 발송 루프는 SMTP 를 트랜잭션 밖에서 부르므로, 쓰기 메서드마다 짧은 트랜잭션을 건다.
 */
public interface DatingNoticeMailRepository extends JpaRepository<DatingNoticeMail, Long> {

    /**
     * 이 수신자를 SENDING 으로 선점한다. 처음 보는 수신자이거나 직전 시도가 FAILED 면 1, 이미 SENT·SENDING 이면 0.
     * "조회 후 삽입"이 아니라 한 문장이어야 동시에 두 번 눌러도 한쪽만 1 을 받는다.
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO dating_notice_mail (campaign_key, profile_id, status)
            VALUES (:campaignKey, :profileId, 'SENDING')
            ON CONFLICT (campaign_key, profile_id) DO UPDATE
                SET status = 'SENDING', updated_at = now()
                WHERE dating_notice_mail.status = 'FAILED'
            """, nativeQuery = true)
    int claim(@Param("campaignKey") String campaignKey, @Param("profileId") UUID profileId);

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE dating_notice_mail SET status = :status, updated_at = now()
            WHERE campaign_key = :campaignKey AND profile_id = :profileId
            """, nativeQuery = true)
    int mark(@Param("campaignKey") String campaignKey, @Param("profileId") UUID profileId,
             @Param("status") String status);

    @Query("SELECT m.status, COUNT(m) FROM DatingNoticeMail m WHERE m.campaignKey = :campaignKey GROUP BY m.status")
    List<Object[]> countByStatus(@Param("campaignKey") String campaignKey);
}
