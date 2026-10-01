package com.darkness.wks.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 일괄 안내 메일 요청 (#159). 문구를 요청 본문으로 받는 이유: 발송 직전 오타를 재배포 없이 고치고 TEST 로 미리 받아 본다.
 *
 * @param campaignKey 중복 방지 단위. 같은 키로 다시 SEND 하면 아직 안 간 사람·실패한 사람에게만 간다
 * @param testTo      mode 가 TEST 일 때만 쓴다
 */
public record AdminNoticeMailRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "[a-z0-9-]+") String campaignKey,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 10000) String body,
        @NotNull Mode mode,
        @Email String testTo) {

    public enum Mode {
        /** 대상 수만 센다. 보내지도 기록하지도 않는다 */
        DRY_RUN,
        /** testTo 한 곳으로만 보낸다. 기록하지 않는다 */
        TEST,
        /** 대상 전원에게 백그라운드로 보낸다 */
        SEND
    }
}
