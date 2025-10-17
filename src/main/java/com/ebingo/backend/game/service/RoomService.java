package com.ebingo.backend.game.service;

import com.ebingo.backend.game.dto.RoomCreateDto;
import com.ebingo.backend.game.dto.RoomDto;
import com.ebingo.backend.game.dto.RoomUpdateDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RoomService {
    Mono<RoomDto> createRoom(RoomCreateDto roomDto, Long telegramId);

    Mono<RoomDto> getRoomById(Long id);

    Flux<RoomDto> getAllRooms();

    Mono<RoomDto> updateRoomById(Long id, RoomUpdateDto roomDto);

    Mono<Void> deleteRoomById(Long id);
}
