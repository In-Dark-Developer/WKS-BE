package com.darkness.wks.wallet;

import com.darkness.wks.common.auth.CurrentMember;
import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.wallet.dto.CheckInResponse;
import com.darkness.wks.wallet.dto.WalletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

@Tag(name = "Wallet", description = "실(재화) 잔액·출석. 로그인 쿠키(wks_token) 필요")
@SecurityRequirement(name = "cookieAuth")
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int CHECK_IN_AMOUNT = 5;

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @Operation(summary = "실 잔액", description = "잔액과 오늘 출석 가능 여부를 돌려준다 (plan.md §8.7).")
    @GetMapping
    public ApiResponse<WalletResponse> wallet(@CurrentMember Long memberId) {
        int balance = walletService.getBalance(memberId);
        boolean canCheckIn = !walletService.hasCredited(LedgerReason.CHECK_IN, memberId, today());
        return ApiResponse.success(new WalletResponse(balance, canCheckIn));
    }

    @Operation(summary = "출석 체크", description = """
            출석 +5 (KST 날짜 기준 1일 1회). 오늘 이미 했으면 지급 없이 checkedIn: false 로 200 응답한다
            — 재클릭이 에러로 뜨지 않게 한다.
            """)
    @PostMapping("/check-in")
    public ApiResponse<CheckInResponse> checkIn(@CurrentMember Long memberId) {
        boolean credited = walletService.credit(memberId, LedgerReason.CHECK_IN, today(), CHECK_IN_AMOUNT);
        return ApiResponse.success(new CheckInResponse(credited, walletService.getBalance(memberId)));
    }

    private static String today() {
        return LocalDate.now(KST).toString();
    }
}
