package com.ebingo.backend.payment.mappers;

import com.ebingo.backend.payment.dto.PaymentMethodDto;
import com.ebingo.backend.payment.entity.PaymentMethod;

public final class PaymentMethodMapper {
    public static PaymentMethodDto toDto(PaymentMethod paymentMethod) {
        return PaymentMethodDto.builder()
                .id(paymentMethod.getId())
                .name(paymentMethod.getName())
                .description(paymentMethod.getDescription())
                .isDefault(paymentMethod.getIsDefault())
                .build();
    }
}
