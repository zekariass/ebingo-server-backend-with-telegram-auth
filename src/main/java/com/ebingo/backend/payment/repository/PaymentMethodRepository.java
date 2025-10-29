package com.ebingo.backend.payment.repository;


import com.ebingo.backend.payment.entity.PaymentMethod;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface PaymentMethodRepository extends ReactiveCrudRepository<PaymentMethod, Long> {
//    Mono<PaymentMethod> findByCode(String code);
}

