package com.ebingo.backend.user.dto;

import com.ebingo.backend.user.enums.UserRole;
import com.ebingo.backend.user.enums.UserStatus;
import lombok.*;

import java.time.LocalDateTime;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class UserProfileDto {
    private Long id;
    private Long telegramId;
    private String firstName;
    private String lastName;
    private String phone;
    private UserStatus status;
    private UserRole role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
