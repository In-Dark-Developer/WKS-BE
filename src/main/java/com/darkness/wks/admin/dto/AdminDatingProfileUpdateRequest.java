package com.darkness.wks.admin.dto;

import com.darkness.wks.common.ContactMethod;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 부분 수정. null 인 필드는 건드리지 않는다. 길이·형식 규칙은 DatingProfileRequest 와 같다. */
public record AdminDatingProfileUpdateRequest(
        @Email @Size(max = 255) String email,
        @Size(min = 1, max = 50) String name,
        ContactMethod contactMethod,
        @Size(min = 1, max = 100) String contactValue,
        @Size(min = 1, max = 100) String department,
        @Pattern(regexp = "^[EI][SN][TF][JP]$") String mbti,
        @Size(min = 1, max = 500) String bio
) {
}
