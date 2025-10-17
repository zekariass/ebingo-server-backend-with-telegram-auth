package com.ebingo.backend.payment.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class WithdrawRequestDto {
    private Long paymentMethodId;
    private BigDecimal amount;
    private String bankName;
    private String accountName;
    private String accountNumber;
}
