package com.ebingo.backend.user.repository;

import com.ebingo.backend.user.entity.UserProfile;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface UserProfileRepository extends ReactiveCrudRepository<UserProfile, Long> {
    Mono<UserProfile> findByPhoneNumber(String phoneNumber);

    Mono<UserProfile> findByTelegramId(Long telegramId);
}
