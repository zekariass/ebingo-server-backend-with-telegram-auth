package com.ebingo.backend.payment.mappers;

import com.ebingo.backend.payment.dto.WalletDto;
import com.ebingo.backend.payment.entity.Wallet;

public final class WalletMapper {
    public static WalletDto toDto(Wallet wallet) {
        return WalletDto.builder()
                .id(wallet.getId())
                .userProfileId(wallet.getUserProfileId())
                .totalDeposit(wallet.getTotalDeposit())
                .welcomeBonus(wallet.getWelcomeBonus())
                .availableWelcomeBonus(wallet.getAvailableWelcomeBonus())
                .referralBonus(wallet.getReferralBonus())
                .availableReferralBonus(wallet.getAvailableReferralBonus())
                .totalPrizeAmount(wallet.getTotalPrizeAmount())
                .pendingWithdrawal(wallet.getPendingWithdrawal())
                .totalWithdrawal(wallet.getTotalWithdrawal())
                .totalAvailableBalance(wallet.getTotalAvailableBalance())
                .availableToWithdraw(wallet.getAvailableToWithdraw())
                .build();
    }

    public static Wallet toEntity(WalletDto walletDto) {
        Wallet wallet = new Wallet();
        wallet.setId(walletDto.getId());
        wallet.setUserProfileId(walletDto.getUserProfileId());
        wallet.setTotalDeposit(walletDto.getTotalDeposit());
//        wallet.setDepositBalance(walletDto.getDepositBalance());
//        wallet.setPendingBalance(walletDto.getPendingBalance());
        wallet.setWelcomeBonus(walletDto.getWelcomeBonus());
        wallet.setAvailableWelcomeBonus(walletDto.getAvailableWelcomeBonus());
        wallet.setReferralBonus(walletDto.getReferralBonus());
        wallet.setAvailableReferralBonus(walletDto.getAvailableReferralBonus());
        wallet.setTotalPrizeAmount(walletDto.getTotalPrizeAmount());
        wallet.setPendingWithdrawal(walletDto.getPendingWithdrawal());
        wallet.setTotalWithdrawal(walletDto.getTotalWithdrawal());
        wallet.setTotalAvailableBalance(walletDto.getTotalAvailableBalance());
        wallet.setAvailableToWithdraw(walletDto.getAvailableToWithdraw());
        return wallet;
    }
}
