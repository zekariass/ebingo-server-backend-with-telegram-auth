package com.ebingo.backend.payment.mappers;

import com.ebingo.backend.payment.dto.PaymentOrderDto;
import com.ebingo.backend.payment.dto.PaymentOrderRequestDto;
import com.ebingo.backend.payment.dto.PaymentOrderResponseDto;
import com.ebingo.backend.payment.entity.PaymentOrder;

import java.time.Instant;

public final class PaymentOrderMapper {
    public static PaymentOrder toEntity(PaymentOrderRequestDto dto, String txnRef) {
        if (dto == null) {
            return null;
        }
        PaymentOrder p = new PaymentOrder();
        p.setUserId(dto.getUserId());
        p.setTxnRef(txnRef);
        p.setAmount(dto.getAmount());
        p.setCurrency(dto.getCurrency() == null ? "ETB" : dto.getCurrency());
        p.setReason(dto.getReason());
        p.setPaymentMethodId(dto.getPaymentMethodId());
        p.setTxnType(dto.getTxnType()); // set appropriately if needed
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        return p;
    }

    public static PaymentOrderResponseDto toResponseDto(PaymentOrder p) {
        if (p == null) {
            return null;
        }
        return PaymentOrderResponseDto.builder()
                .orderId(p.getId())
                .txnRef(p.getTxnRef())
                .status(p.getStatus())
                .amount(p.getAmount())
                .providerUuid(p.getProviderOrderRef())
                .instructionsUrl(p.getInstructionsUrl())
                .checkoutUrl(p.getInstructionsUrl())
                .build();
    }

    public static PaymentOrderDto toDto(PaymentOrder order) {
        if (order == null) {
            return null;
        }
        return PaymentOrderDto.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .txnRef(order.getTxnRef())
                .providerOrderRef(order.getProviderOrderRef())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .status(order.getStatus())
                .reason(order.getReason())
                .paymentMethodId(order.getPaymentMethodId())
                .instructionsUrl(order.getInstructionsUrl())
                .txnType(order.getTxnType())
                .metaData(order.getMetaData())
                .approvedBy(order.getApprovedBy())
                .nonce(order.getNonce())
                .phoneNumber(order.getPhoneNumber())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
