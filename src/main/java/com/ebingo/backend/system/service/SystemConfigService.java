package com.ebingo.backend.system.service;

import com.ebingo.backend.system.dto.SystemConfigDto;
import reactor.core.publisher.Mono;

public interface SystemConfigService {
    Mono<SystemConfigDto> getSystemConfig(String name);
}
