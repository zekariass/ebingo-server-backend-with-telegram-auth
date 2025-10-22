package com.ebingo.backend.system.service;

import com.ebingo.backend.system.Repository.SystemConfigRepository;
import com.ebingo.backend.system.dto.SystemConfigDto;
import com.ebingo.backend.system.exceptions.ResourceNotFoundException;
import com.ebingo.backend.system.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {
    private final SystemConfigRepository systemConfigRepository;

    @Override
    public Mono<SystemConfigDto> getSystemConfig(String name) {
        return systemConfigRepository.findByName(name)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("SYSTEM_CONFIG_NOT_FOUND")))
                .map(SystemConfigMapper::toDto)
                .doOnSubscribe(s -> log.info("Fetching system config {}", name))
                .doOnSuccess(s -> log.info("Fetched system config {}", name))
                .doOnError(e -> log.error("Failed to fetch system config {}", name, e));
    }

    @Override
    public Flux<SystemConfigDto> getAllSystemConfigs() {
        return systemConfigRepository.findAll()
                .map(SystemConfigMapper::toDto)
                .doOnSubscribe(s -> log.info("Fetching all system configs"))
                .doOnComplete(() -> log.info("Fetched all system configs"))
                .doOnError(e -> log.error("Failed to fetch all system configs", e));
    }


}
