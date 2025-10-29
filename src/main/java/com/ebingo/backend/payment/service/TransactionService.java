package com.ebingo.backend.payment.service;

import com.ebingo.backend.payment.dto.TransactionDto;
import com.ebingo.backend.payment.entity.PaymentOrder;
import reactor.core.publisher.Mono;

public interface TransactionService {
    
    Mono<TransactionDto> createTransaction(PaymentOrder order);
}
