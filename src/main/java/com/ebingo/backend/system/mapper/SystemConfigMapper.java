package com.ebingo.backend.system.mapper;

import com.ebingo.backend.system.dto.SystemConfigDto;
import com.ebingo.backend.system.entity.SystemConfig;

public final class SystemConfigMapper {
    public static SystemConfig toEntity(SystemConfigDto dto) {
        if (dto == null) return null;
        SystemConfig entity = new SystemConfig();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setValue(dto.getValue());
        return entity;
    }

    public static SystemConfigDto toDto(SystemConfig entity) {
        if (entity == null) return null;
        SystemConfigDto dto = new SystemConfigDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setValue(entity.getValue());
        return dto;
    }
}
