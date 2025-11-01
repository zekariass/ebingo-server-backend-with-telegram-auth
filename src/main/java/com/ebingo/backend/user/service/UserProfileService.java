package com.ebingo.backend.user.service;


import com.ebingo.backend.user.dto.UserProfileCreateDto;
import com.ebingo.backend.user.dto.UserProfileDto;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

public interface UserProfileService {

    Mono<UserProfileDto> getUserProfileByPhoneNumber(String PhoneNumber);

    Mono<UserProfileDto> createUserProfile(UserProfileCreateDto userProfileDto);

    Mono<UserProfileDto> getUserProfileById(@NotNull(message = "Receiver id is required") Long receiverId);

    Mono<UserProfileDto> getUserByPhoneNumber(@NotNull(message = "Email id is required") String email);

    Mono<UserProfileDto> getUserProfileByTelegramId(Long telegramId);

    Mono<UserProfileDto> changeName(Long telegramId, String name);
}
