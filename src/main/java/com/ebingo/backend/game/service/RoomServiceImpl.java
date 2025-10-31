package com.ebingo.backend.game.service;

import com.ebingo.backend.game.dto.RoomCreateDto;
import com.ebingo.backend.game.dto.RoomDto;
import com.ebingo.backend.game.dto.RoomUpdateDto;
import com.ebingo.backend.game.entity.Room;
import com.ebingo.backend.game.enums.RoomStatus;
import com.ebingo.backend.game.mappers.RoomMapper;
import com.ebingo.backend.game.repository.RoomRepository;
import com.ebingo.backend.game.service.state.GameStateService;
import com.ebingo.backend.system.exceptions.ResourceNotFoundException;
import com.ebingo.backend.user.service.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final ReactiveTransactionManager transactionManager;
    private final GameStateService gameStateService;
    private final UserProfileService userProfileRepository;

    @Value("${telegram.bot.token}")
    private String botToken; //= "8315167211:AAGvx8p3ovgm6snPgtEf7JiaBgXjNhpSizY";

    public RoomServiceImpl(RoomRepository roomRepository, ReactiveTransactionManager transactionManager, GameStateService gameStateService, UserProfileService userProfileRepository) {
        this.roomRepository = roomRepository;
        this.transactionManager = transactionManager;
        this.gameStateService = gameStateService;
        this.userProfileRepository = userProfileRepository;
    }


    @Override
    public Mono<RoomDto> createRoom(RoomCreateDto roomDto, Long telegramId) {
        log.info("Creating room");

        TransactionalOperator operator = TransactionalOperator.create(transactionManager);
        Room room = RoomMapper.toEntity(roomDto);

        return userProfileRepository.getUserProfileByTelegramId(telegramId)
                .switchIfEmpty(Mono.error(new IllegalStateException("User profile not found for telegramId: " + telegramId)))
                .flatMap(userProfile -> {
                    room.setCreatedBy(userProfile.getId());
                    return roomRepository.save(room)
                            .map(RoomMapper::toDto)
                            .doOnSuccess(r -> log.info(" Room created successfully: {}", r.getName()))
                            .doOnError(e -> log.error(" Error creating room: {}", roomDto, e));
                })
                .as(operator::transactional);
    }


    @Override
    public Mono<RoomDto> getRoomById(Long id) {
//        log.info("===============================>> Getting room by id: {}", id);
        return roomRepository.findById(id)
                .onErrorMap(e -> new RuntimeException("Error getting room by id: " + id, e))
                .map(RoomMapper::toDto);
    }

    @Override
    public Flux<RoomDto> getAllRooms() {
        return roomRepository.findByStatus(RoomStatus.OPEN)
                .doOnSubscribe(s -> log.info("Fetching all rooms"))
                .map(RoomMapper::toDto)
                .doOnNext(dto -> log.debug("Mapped room: {}", dto))
                .onErrorMap(e -> {
                    log.error("Error fetching rooms", e);
                    return new RuntimeException("Error getting all rooms", e);
                });
    }


    @Override
    public Mono<RoomDto> updateRoomById(Long id, RoomUpdateDto roomDto) {
        log.info("Updating room by id: {}", id);

        System.out.println("=====================================>>>: " + roomDto);
        return roomRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Room not found with id: " + id)))
                .flatMap(existingRoom -> {
                    RoomMapper.toEntity(roomDto, existingRoom); // mutate fields
                    return roomRepository.save(existingRoom);
                })
                .map(RoomMapper::toDto)
                .onErrorMap(e -> new RuntimeException("Error updating room with id: " + id, e));
    }


    @Override
    public Mono<Void> deleteRoomById(Long id) {

        Mono<Void> deleteGameState = gameStateService.deleteGameState(id)
                .doOnSuccess(deleted -> log.info("Deleted game state for roomId={} -> {}", id, deleted))
                .doOnError(e -> log.error("Error deleting game state for roomId={}", id, e))
                .then(); // convert Mono<Boolean> to Mono<Void>

        Mono<Void> deleteRoom = roomRepository.deleteById(id)
                .doOnSuccess(v -> log.info("Deleted room with id={}", id))
                .doOnError(e -> log.error("Error deleting room with id={}", id, e));

        // Run both in parallel and wait for both to complete
        return Mono.when(deleteRoom, deleteGameState);
    }

}
