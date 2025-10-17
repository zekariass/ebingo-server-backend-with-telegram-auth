package com.ebingo.backend.system.Repository;

import com.ebingo.backend.system.entity.SystemConfig;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface SystemConfigRepository extends ReactiveCrudRepository<SystemConfig, Long> {
    Mono<SystemConfig> findByName(String name);
}
