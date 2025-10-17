package com.ebingo.backend.user.mappers;


import com.ebingo.backend.user.dto.UserProfileCreateDto;
import com.ebingo.backend.user.dto.UserProfileDto;
import com.ebingo.backend.user.dto.UserProfileUpdateDto;
import com.ebingo.backend.user.entity.UserProfile;
import com.ebingo.backend.user.enums.UserRole;
import com.ebingo.backend.user.enums.UserStatus;

public final class UserProfileMapper {

    public static UserProfileDto toDto(UserProfile userProfile) {
        if (userProfile == null) return null;
        return UserProfileDto.builder()
                .id(userProfile.getId())
                .telegramId(userProfile.getTelegramId())
                .firstName(userProfile.getFirstName())
                .lastName(userProfile.getLastName())
                .phone(userProfile.getPhoneNumber())
                .status(userProfile.getStatus())
                .role(userProfile.getRole())
                .createdAt(userProfile.getCreatedAt())
                .updatedAt(userProfile.getUpdatedAt())
                .build();
    }

    public static UserProfile toEntity(UserProfileCreateDto userProfileDto) {
        if (userProfileDto == null) return null;
        UserProfile userProfile = new UserProfile();
        userProfile.setTelegramId(userProfileDto.getTelegramId());
        userProfile.setFirstName(userProfileDto.getFirstName());
        userProfile.setLastName(userProfileDto.getLastName());
        userProfile.setPhoneNumber(userProfileDto.getPhoneNumber());
        userProfile.setStatus(UserStatus.ACTIVE); // Default status
        userProfile.setRole(UserRole.PLAYER);
        return userProfile;
    }

    public static UserProfile toEntity(UserProfileDto userProfileDto) {
        if (userProfileDto == null) return null;
        UserProfile userProfile = new UserProfile();
        userProfile.setId(userProfileDto.getId());
        userProfile.setFirstName(userProfileDto.getFirstName());
        userProfile.setLastName(userProfileDto.getLastName());
        userProfile.setPhoneNumber(userProfileDto.getPhone());
        userProfile.setStatus(UserStatus.ACTIVE); // Default status
        userProfile.setRole(userProfileDto.getRole() != null ? userProfileDto.getRole() : UserRole.PLAYER);
        return userProfile;
    }

    public static UserProfile toEntity(UserProfileUpdateDto userProfileDto, UserProfile existingUserProfile) {

        if (userProfileDto == null) return null;
        if (existingUserProfile == null) return null;
        if (userProfileDto.getFirstName() != null) {
            existingUserProfile.setFirstName(userProfileDto.getFirstName());
        }
        if (userProfileDto.getLastName() != null) {
            existingUserProfile.setLastName(userProfileDto.getLastName());
        }
        return existingUserProfile;
    }


}
