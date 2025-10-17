package com.ebingo.backend.payment.service;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface PaymentService {

    Mono<Boolean> processPayment(String userId, BigDecimal amount, Long gameId);

    Mono<Boolean> processRefund(String userId, Long gameId);
}
