package com.ebingo.backend.payment.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PaymentMethodDto {
    private Long id;
    private String name;
    private String description;
    private Boolean isDefault;

}

