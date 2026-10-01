package com.darkness.wks.admin.dto;

/**
 * SEND 는 응답을 먼저 돌려주고 백그라운드로 보내므로 진행 상황은 이걸로 본다.
 *
 * @param sending 발송 중. 발송이 끝났는데 남아 있으면 그 사이 앱이 재기동된 것 — 자동 재발송하지 않는다
 */
public record AdminNoticeMailStatusResponse(String campaignKey, int targets, long sent, long failed, long sending) {
}
