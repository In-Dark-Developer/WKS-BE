package com.darkness.wks.admin.dto;

/** @param targets 지금 기준 발송 대상 수(비활성 아닌 소개팅 프로필). 이미 SENT 인 사람도 포함한다 */
public record AdminNoticeMailResponse(String campaignKey, AdminNoticeMailRequest.Mode mode, int targets) {
}
