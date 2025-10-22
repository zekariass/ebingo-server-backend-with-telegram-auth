package com.ebingo.backend.payment.service;

import com.ebingo.backend.payment.dto.DepositTransferDto;
import com.ebingo.backend.payment.dto.DepositTransferRequestDto;
import com.ebingo.backend.payment.entity.DepositTransfer;
import com.ebingo.backend.payment.entity.Wallet;
import com.ebingo.backend.payment.enums.TransferStatus;
import com.ebingo.backend.payment.mappers.DepositTransferMapper;
import com.ebingo.backend.payment.mappers.WalletMapper;
import com.ebingo.backend.payment.repository.DepositTransferRepository;
import com.ebingo.backend.system.exceptions.InsufficientBalanceException;
import com.ebingo.backend.system.exceptions.ResourceNotFoundException;
import com.ebingo.backend.user.mappers.UserProfileMapper;
import com.ebingo.backend.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class DepositTransferServiceImpl implements DepositTransferService {

    private final DepositTransferRepository depositTransferRepository;
    private final UserProfileService userProfileService;
    private final WalletService walletService;
    private final TransactionalOperator transactionalOperator;


    @Override
    public Flux<DepositTransferDto> getPaginatedDepositTransfer(Long telegramId, Integer page, Integer size, String sortBy) {

        int pageNumber = (page != null && page >= 0) ? page : 0;
        int pageSize = (size != null && size > 0 && size <= 100) ? size : 10;
        long offset = (long) pageNumber * pageSize;

        return userProfileService.getUserProfileByTelegramId(telegramId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User profile not found")))
                .flatMapMany(up -> {
                    String sortKey = (sortBy != null) ? sortBy.toLowerCase() : "id";
                    return switch (sortKey) {
                        case "amount" -> depositTransferRepository
                                .findBySenderIdOrderByAmountDesc(up.getId(), pageSize, offset);
                        case "createdat" -> depositTransferRepository
                                .findBySenderIdOrderByCreatedAtDesc(up.getId(), pageSize, offset);
                        default -> depositTransferRepository
                                .findBySenderIdOrderByIdDesc(up.getId(), pageSize, offset);
                    };
                })
                .map(DepositTransferMapper::toDto)
                .doOnSubscribe(s -> log.info("Fetching deposit transfers - Page: {}, Size: {}, SortBy: {}", page, size, sortBy))
                .doOnComplete(() -> log.info("Completed fetching deposit transfers for user: {}", telegramId))
                .doOnError(e -> log.error("Failed to fetch deposit transfers: {}", e.getMessage(), e));
    }

    @Override
    public Mono<DepositTransferDto> getASingleDepositTransfer(Long id, Long telegramId) {
        return userProfileService.getUserProfileByTelegramId(telegramId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User profile not found")))
                .flatMap(up ->
                        depositTransferRepository.findByIdAndSenderId(id, up.getId())
                                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Deposit transfer not found")))
                )
                .map(DepositTransferMapper::toDto)
                .doOnSubscribe(s -> log.info("Fetching deposit transfer: {}", id))
                .doOnSuccess(dto -> log.info("Completed fetching deposit transfer: {}", id))
                .doOnError(e -> log.error("Failed to fetch deposit transfer: {}", e.getMessage(), e));
    }


    @Override
    public Mono<Void> deleteDepositTransfer(Long id, Long telegramId) {

        return userProfileService.getUserProfileByTelegramId(telegramId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User profile not found")))
                .flatMap(userProfile ->
                        depositTransferRepository.findByIdAndSenderId(id, userProfile.getId())
                                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Deposit transfer not found")))
                                .flatMap(depositTransferRepository::delete)
                )
                .doOnSubscribe(s -> log.info("Deleting deposit transfer: {}", id))
                .doOnSuccess(v -> log.info("Deleted deposit transfer successfully: {}", id))
                .doOnError(e -> log.error("Failed to delete deposit transfer: {}", e.getMessage(), e));
    }


    @Override
    public Mono<DepositTransferDto> createDepositTransfer(
            DepositTransferRequestDto depositTransferDto,
            Long telegramId
    ) {
        log.info("Initiating deposit transfer: {}", depositTransferDto);

        BigDecimal amount = depositTransferDto.getAmount();

        Mono<DepositTransferDto> transferMono = userProfileService.getUserProfileByTelegramId(telegramId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Sender profile not found")))
                .flatMap(senderProfile ->
                        // Get or create sender wallet
                        walletService.getWalletByUserProfileId(senderProfile.getId())
                                .switchIfEmpty(walletService.createWallet(UserProfileMapper.toEntity(senderProfile)))
                                .flatMap(senderWallet ->
                                        // Get or create receiver wallet
                                        userProfileService.getUserProfileByPhoneNumber(depositTransferDto.getPhoneNumber())
                                                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Receiver profile not found")))
                                                .flatMap(receiverProfile ->
                                                        walletService.getWalletByUserProfileId(receiverProfile.getId())
                                                                .switchIfEmpty(walletService.createWallet(UserProfileMapper.toEntity(receiverProfile)))
                                                                .flatMap(receiverWallet -> {
                                                                    // Check sender balance
                                                                    if (senderWallet.getTotalAvailableBalance().compareTo(amount) < 0) {
                                                                        // Insufficient balance — create failed transfer record
                                                                        DepositTransfer failedTransfer = new DepositTransfer();
                                                                        failedTransfer.setSenderId(senderProfile.getId());
                                                                        failedTransfer.setReceiverId(receiverProfile.getId());
                                                                        failedTransfer.setAmount(amount);
                                                                        failedTransfer.setStatus(TransferStatus.FAIL);
                                                                        failedTransfer.setCreatedAt(Instant.now());
                                                                        failedTransfer.setUpdatedAt(Instant.now());
                                                                        return depositTransferRepository.save(failedTransfer)
                                                                                .map(DepositTransferMapper::toDto);
                                                                    }

                                                                    // Update sender and receiver wallets reactively
                                                                    return updateSenderWalletBalance(WalletMapper.toEntity(senderWallet), amount)
                                                                            .flatMap(walletService::saveWallet)
                                                                            .then(creditReceiverWalletBalanceForDeposit(WalletMapper.toEntity(receiverWallet), amount)
                                                                                    .flatMap(walletService::saveWallet))
                                                                            .then(Mono.defer(() -> {
                                                                                // Create success transfer record
                                                                                DepositTransfer transfer = new DepositTransfer();
                                                                                transfer.setSenderId(senderProfile.getId());
                                                                                transfer.setReceiverId(receiverProfile.getId());
                                                                                transfer.setAmount(amount);
                                                                                transfer.setStatus(TransferStatus.SUCCESS);
                                                                                transfer.setCreatedAt(Instant.now());
                                                                                transfer.setUpdatedAt(Instant.now());
                                                                                return depositTransferRepository.save(transfer);
                                                                            }))
                                                                            .map(DepositTransferMapper::toDto);
                                                                })
                                                )
                                )
                );

        // Execute in reactive transaction
        return this.transactionalOperator.transactional(transferMono)
                .doOnSubscribe(s -> log.info("Starting deposit transfer for Supabase user: {}", telegramId))
                .doOnSuccess(dto -> log.info("Deposit transfer completed successfully: {}", dto))
                .doOnError(e -> log.error("Failed to complete deposit transfer: {}", e.getMessage(), e));
    }


    public Mono<Wallet> updateSenderWalletBalance(Wallet senderWallet, BigDecimal amount) {
        return Mono.fromSupplier(() -> {
            BigDecimal remaining = amount;

//            BigDecimal deposit = senderWallet.getDepositBalance();
            BigDecimal referral = senderWallet.getAvailableReferralBonus();
            BigDecimal welcome = senderWallet.getAvailableWelcomeBonus();


//            // Deduct from deposit balance first
//            if (deposit.compareTo(BigDecimal.ZERO) > 0) {
//                BigDecimal used = deposit.min(remaining);
//                senderWallet.setDepositBalance(deposit.subtract(used));
//                remaining = remaining.subtract(used);
//            }

            // Deduct from referral bonus
            if (remaining.compareTo(BigDecimal.ZERO) > 0 && referral.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal used = referral.min(remaining);
                senderWallet.setAvailableReferralBonus(referral.subtract(used));
                senderWallet.setReferralBonus(senderWallet.getReferralBonus().subtract(used));
                remaining = remaining.subtract(used);
            }

            // Deduct from welcome bonus
            if (remaining.compareTo(BigDecimal.ZERO) > 0 && welcome.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal used = welcome.min(remaining);
                senderWallet.setAvailableWelcomeBonus(welcome.subtract(used));
                senderWallet.setWelcomeBonus(senderWallet.getWelcomeBonus().subtract(used));
                remaining = remaining.subtract(used);
            }

            // deduct from total available balance
            if (remaining.compareTo(BigDecimal.ZERO) > 0
                    && senderWallet.getTotalAvailableBalance().compareTo(BigDecimal.ZERO) > 0) {

                BigDecimal used = senderWallet.getTotalAvailableBalance().min(remaining);

                senderWallet.setTotalAvailableBalance(senderWallet.getTotalAvailableBalance().subtract(used));

                BigDecimal updatedWithdrawable = senderWallet.getAvailableToWithdraw().subtract(used);
                if (updatedWithdrawable.compareTo(BigDecimal.ZERO) < 0) {
                    updatedWithdrawable = BigDecimal.ZERO;
                }
                senderWallet.setAvailableToWithdraw(updatedWithdrawable);

                remaining = remaining.subtract(used);
            }


            // Insufficient funds check
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                throw new InsufficientBalanceException("Insufficient wallet balance to complete transfer");
            }

            // Recalculate totals
//            senderWallet.setTotalAvailableBalance(
//                    senderWallet.getDepositBalance()
//                            .add(senderWallet.getAvailableReferralBonus())
//                            .add(senderWallet.getAvailableWelcomeBonus())
//            );

//            senderWallet.setAvailableToWithdraw(
//                    senderWallet.getDepositBalance()
//                            .add(senderWallet.getAvailableReferralBonus())
//            );

            return senderWallet;
        });
    }


    @Override
    public Mono<Wallet> creditReceiverWalletBalanceForDeposit(Wallet receiverWallet, BigDecimal amount) {
        return Mono.fromSupplier(() -> {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Transfer amount must be greater than zero");
            }
            receiverWallet.setTotalDeposit(
                    receiverWallet.getTotalDeposit().add(amount)
            );

            // Update total available and withdrawable balances
            receiverWallet.setTotalAvailableBalance(
                    receiverWallet.getTotalAvailableBalance().add(amount)
            );
            receiverWallet.setAvailableToWithdraw(
                    receiverWallet.getAvailableToWithdraw().add(amount)
            );

            // Optionally update timestamps
            receiverWallet.setUpdatedAt(Instant.now());

            return receiverWallet;
        });
    }


}
