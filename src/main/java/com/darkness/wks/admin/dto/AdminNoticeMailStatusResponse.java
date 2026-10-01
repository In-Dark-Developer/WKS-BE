package com.darkness.wks.admin.dto;

import java.time.Instant;

/**
 * SEND·예약 발송은 백그라운드로 돌므로 진행 상황은 이걸로 본다.
 *
 * @param sending   발송 중. 발송이 끝났는데 남아 있으면 그 사이 앱이 재기동된 것 — 자동 재발송하지 않는다
 * @param state     예약 상태 SCHEDULED·STARTED·EXPIRED. 예약 없이 SEND 만 했으면 null
 * @param sendAt    예약 시각. 예약이 없으면 null
 * @param startedAt 예약이 잡힌(발송 시작·만료 판정) 시각. 아직이면 null
 */
public record AdminNoticeMailStatusResponse(String campaignKey, int targets, long sent, long failed, long sending,
                                            String state, Instant sendAt, Instant startedAt) {
}
