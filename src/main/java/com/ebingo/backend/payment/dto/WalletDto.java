package com.ebingo.backend.payment.dto;

import lombok.*;

import java.math.BigDecimal;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class WalletDto {
    private Long id;

    private Long userProfileId;

    private BigDecimal totalDeposit;

    private BigDecimal welcomeBonus;

    private BigDecimal availableWelcomeBonus;

    private BigDecimal referralBonus;

    private BigDecimal availableReferralBonus;

    private BigDecimal totalPrizeAmount;

    private BigDecimal pendingWithdrawal;

    private BigDecimal totalWithdrawal;

    private BigDecimal totalAvailableBalance;

    private BigDecimal availableToWithdraw;
}
