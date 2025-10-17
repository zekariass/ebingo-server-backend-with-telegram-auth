package com.ebingo.backend.game.service;

import com.ebingo.backend.game.dto.BingoClaimDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BingoClaimService {
    Mono<BingoClaimDto> createBingoClaim(BingoClaimDto bingoClaim);

    Flux<BingoClaimDto> getPaginatedBingoClaim(String phoneNumber, Integer page, Integer size, String sortBy);

    Mono<BingoClaimDto> getBingoClaimById(String userSupabaseId, Long id);
}
