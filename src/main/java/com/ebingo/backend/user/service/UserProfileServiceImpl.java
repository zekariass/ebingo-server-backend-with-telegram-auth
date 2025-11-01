package com.ebingo.backend.user.service;

import com.ebingo.backend.system.exceptions.ResourceNotFoundException;
import com.ebingo.backend.user.dto.UserProfileCreateDto;
import com.ebingo.backend.user.dto.UserProfileDto;
import com.ebingo.backend.user.entity.UserProfile;
import com.ebingo.backend.user.mappers.UserProfileMapper;
import com.ebingo.backend.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final ReactiveTransactionManager transactionManager;


    @Override
    public Mono<UserProfileDto> getUserProfileByPhoneNumber(String phoneNumber) {
        log.info("Getting user profile by phone number");

        return userProfileRepository.findByPhoneNumber(phoneNumber)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found")))
                .map(UserProfileMapper::toDto);
    }

    @Override
    public Mono<UserProfileDto> createUserProfile(UserProfileCreateDto userProfileDto) {
        log.info("Creating user profile: {}", userProfileDto);

        UserProfile userProfile = UserProfileMapper.toEntity(userProfileDto);

        TransactionalOperator operator = TransactionalOperator.create(transactionManager);

        return userProfileRepository.save(userProfile)
                .map(UserProfileMapper::toDto)
                .doOnSuccess(saved -> log.info("User profile created successfully: {}", saved))
                .doOnError(e -> log.error("Error creating user profile: {}", userProfileDto, e))
                .as(operator::transactional);
    }

    @Override
    public Mono<UserProfileDto> getUserProfileById(Long receiverId) {
        return userProfileRepository.findById(receiverId)
                .map(UserProfileMapper::toDto)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found")))
                .doOnSubscribe(s -> log.info("Fetching user profile: {}", receiverId))
                .doOnSuccess(dto -> log.info("Completed fetching user profile: {}", receiverId))
                .doOnError(e -> log.error("Failed to fetch user profile: {}", e.getMessage(), e));
    }

    @Override
    public Mono<UserProfileDto> getUserByPhoneNumber(String phoneNumber) {
        return userProfileRepository.findByPhoneNumber(phoneNumber)
                .map(UserProfileMapper::toDto)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found")))
                .doOnSubscribe(s -> log.info("Fetching user profile by phone number"))
                .doOnSuccess(dto -> log.info("Completed fetching user profile by phoneNumber"))
                .doOnError(e -> log.error("Failed to fetch user profile by email: {}", e.getMessage(), e));
    }

    @Override
    public Mono<UserProfileDto> getUserProfileByTelegramId(Long telegramId) {
        return userProfileRepository.findByTelegramId(telegramId)
                .map(UserProfileMapper::toDto)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found for telegramId: " + telegramId)))
                .doOnSubscribe(s -> log.info("Fetching user profile for telegramId: {}", telegramId))
                .doOnSuccess(dto -> {
                    if (dto != null) {
                        log.info("Successfully fetched user profile for telegramId: {}", telegramId);
                    }
                })
                .doOnError(e -> log.error("Failed to fetch user profile for telegramId: {} - {}", telegramId, e.getMessage(), e));
    }

    @Override
    public Mono<UserProfileDto> changeName(Long telegramId, String name) {
        log.info("Changing nickname for telegramId {}: {}", telegramId, name);

        return userProfileRepository.findByTelegramId(telegramId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found for telegramId: " + telegramId)))
                .flatMap(userProfile -> {
                    userProfile.setNickname(name != null ? name.trim() : null);
                    return userProfileRepository.save(userProfile);
                })
                .map(UserProfileMapper::toDto)
                .doOnSuccess(updated -> log.info("Name changed successfully for telegramId {}: {}", telegramId, updated))
                .doOnError(e -> log.error("Error changing name for telegramId {}: {}", telegramId, e.getMessage(), e));
    }


}
