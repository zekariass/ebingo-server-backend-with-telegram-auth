package com.ebingo.backend.payment.dto;

import com.ebingo.backend.payment.enums.PaymentOrderStatus;
import com.ebingo.backend.payment.enums.WithdrawalMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WithdrawalResponseDto {
    private String data;
    private PaymentOrderStatus status;
    private String detail;
    private String message;
    private WithdrawalMode withdrawalMode;
}
