package com.ebingo.backend.user.controller.secured;

import com.ebingo.backend.common.dto.ApiResponse;
import com.ebingo.backend.user.dto.UserProfileDto;
import com.ebingo.backend.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@Tag(name = "User Profile", description = "User Profile APIs")
@RequestMapping("/api/v1/secured/user-profile")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

//    @GetMapping("/me")
//    @Operation(summary = "Get user profile", description = "Get user profile")
//    public Mono<ResponseEntity<ApiResponse<UserProfileDto>>> getUserProfile(
//            String userPhoneNumber,
//            ServerWebExchange exchange
//    ) {
//        return userProfileService.getUserProfileByPhoneNumber(userPhoneNumber)
//                .map(userProfileDto -> ApiResponse.<UserProfileDto>builder()
//                        .statusCode(HttpStatus.OK.value())
//                        .success(true)
//                        .message("User profile retrieved successfully")
//                        .path(exchange.getRequest().getPath().value())
//                        .timestamp(Instant.now())
//                        .data(userProfileDto)
//                        .build()
//                )
//                .map(ResponseEntity::ok);
//    }

    @GetMapping("/{telegramId}")
    @Operation(summary = "Get user profile", description = "Get user profile")
    public Mono<ResponseEntity<ApiResponse<UserProfileDto>>> getUserProfile(
            @PathVariable Long telegramId,
            ServerWebExchange exchange
    ) {
        System.out.println("================>>>>>>>>>>>>>>>>>>>>>>>: Fetching user profile for telegramId: " + telegramId);
        return userProfileService.getUserProfileBytelegramId(telegramId)
                .map(userProfileDto -> ApiResponse.<UserProfileDto>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("User profile retrieved successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(userProfileDto)
                        .build()
                )
                .map(ResponseEntity::ok);
    }
}
