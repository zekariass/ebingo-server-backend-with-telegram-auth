package com.ebingo.backend.game.controller.secured;

import com.ebingo.backend.common.dto.ApiResponse;
import com.ebingo.backend.common.telegram.TelegramAuthVerifier;
import com.ebingo.backend.game.dto.RoomCreateDto;
import com.ebingo.backend.game.dto.RoomDto;
import com.ebingo.backend.game.dto.RoomUpdateDto;
import com.ebingo.backend.game.service.RoomService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/secured/rooms")
@Tag(name = "Room Secured Controller", description = "Room Secured Controller")
public class RoomController {
    private final RoomService roomService;
    private final TelegramAuthVerifier telegramAuthVerifier;
    private final ObjectMapper objectMapper;

    public RoomController(RoomService roomService, TelegramAuthVerifier telegramAuthVerifier, ObjectMapper objectMapper) {
        this.roomService = roomService;
        this.telegramAuthVerifier = telegramAuthVerifier;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Operation(summary = "Create room", description = "Create room")
    public Mono<ResponseEntity<ApiResponse<RoomDto>>> createRoom(
            @Valid @RequestBody RoomCreateDto roomDto,
            @RequestHeader(value = "x-init-data", required = true) String telegramInitData,
            ServerWebExchange exchange
    ) {

        Optional<Map<String, String>> initData = telegramAuthVerifier.verifyInitData(telegramInitData);
        if (initData.isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.<RoomDto>builder()
                            .statusCode(HttpStatus.UNAUTHORIZED.value())
                            .success(false)
                            .message("Invalid telegram init data")
                            .path(exchange.getRequest().getPath().value())
                            .timestamp(Instant.now())
                            .build()
            ));
        }

        Map<String, Object> user;
        long telegramId;

        try {
            user = objectMapper.readValue(initData.get().get("user"), Map.class);

            if (!user.containsKey("id")) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        ApiResponse.<RoomDto>builder()
                                .statusCode(HttpStatus.UNAUTHORIZED.value())
                                .success(false)
                                .message("Telegram user data does not contain id")
                                .path(exchange.getRequest().getPath().value())
                                .timestamp(Instant.now())
                                .build()
                ));
            }

            telegramId = Long.parseLong(user.get("id").toString());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }


        return roomService.createRoom(roomDto, telegramId)
                .map(createdRoom -> ApiResponse.<RoomDto>builder()
                        .statusCode(HttpStatus.CREATED.value())
                        .success(true)
                        .message("Room created successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(createdRoom)
                        .build()
                )
                .map(response -> ResponseEntity.status(201).body(response));
    }


    @GetMapping("/{id}")
    @Operation(summary = "Get room by ID", description = "Get room by ID")
    public Mono<ResponseEntity<ApiResponse<RoomDto>>> getRoomById(
            @Parameter(required = true, description = "Room ID") @RequestParam Long id,
            @RequestHeader(value = "x-init-data", required = true) String telegramInitData,
            ServerWebExchange exchange) {

        if (telegramAuthVerifier.verifyInitData(telegramInitData).isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.<RoomDto>builder()
                            .statusCode(HttpStatus.UNAUTHORIZED.value())
                            .success(false)
                            .message("Invalid telegram init data")
                            .path(exchange.getRequest().getPath().value())
                            .timestamp(Instant.now())
                            .build()
            ));
        }
        return roomService.getRoomById(id)
                .map(room -> ApiResponse.<RoomDto>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Room retrieved successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(room)
                        .build()
                )
                .map(ResponseEntity::ok);
    }


    @GetMapping
    @Operation(summary = "Get all rooms", description = "Get all rooms")
    public Mono<ResponseEntity<ApiResponse<List<RoomDto>>>> getAllRooms(
            @RequestHeader(value = "x-init-data", required = true) String telegramInitData,
            ServerWebExchange exchange
    ) {

        if (telegramAuthVerifier.verifyInitData(telegramInitData).isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.<List<RoomDto>>builder()
                            .statusCode(HttpStatus.UNAUTHORIZED.value())
                            .success(false)
                            .message("Invalid telegram init data")
                            .path(exchange.getRequest().getPath().value())
                            .timestamp(Instant.now())
                            .build()
            ));
        }

        return roomService.getAllRooms()
                .collectList()
                .map(rooms -> ApiResponse.<List<RoomDto>>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Rooms retrieved successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(rooms)
                        .build())
                .map(ResponseEntity::ok);
    }


    @PutMapping("/{id}")
    @Operation(summary = "Update room by ID", description = "Update room by ID")
    public Mono<ResponseEntity<ApiResponse<RoomDto>>> updateRoomById(
            @Parameter(required = true, description = "Room ID") @PathVariable Long id,
            @Parameter(required = true, description = "Room") @Valid @RequestBody RoomUpdateDto roomDto,
            @RequestHeader(value = "x-init-data", required = true) String telegramInitData,
            ServerWebExchange exchange) {

        if (telegramAuthVerifier.verifyInitData(telegramInitData).isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.<RoomDto>builder()
                            .statusCode(HttpStatus.UNAUTHORIZED.value())
                            .success(false)
                            .message("Invalid telegram init data")
                            .path(exchange.getRequest().getPath().value())
                            .timestamp(Instant.now())
                            .build()
            ));
        }
        return roomService.updateRoomById(id, roomDto)
                .map(updatedRoom -> ApiResponse.<RoomDto>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Room updated successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .data(updatedRoom)
                        .build()
                )
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete room by ID", description = "Delete room by ID")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteRoomById(
            @Parameter(required = true, description = "Room ID") @PathVariable Long id,
            @RequestHeader(value = "x-init-data", required = true) String telegramInitData,
            ServerWebExchange exchange
    ) {
        if (telegramAuthVerifier.verifyInitData(telegramInitData).isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.<Void>builder()
                            .statusCode(HttpStatus.UNAUTHORIZED.value())
                            .success(false)
                            .message("Invalid telegram init data")
                            .path(exchange.getRequest().getPath().value())
                            .timestamp(Instant.now())
                            .build()
            ));
        }
        return roomService.deleteRoomById(id)
                .then(Mono.fromSupplier(() -> ApiResponse.<Void>builder()
                        .statusCode(HttpStatus.OK.value())
                        .success(true)
                        .message("Room deleted successfully")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .build()
                ))
                .map(ResponseEntity::ok);
    }

//    @GetMapping("/{roomId}/pool")
//    public Mono<Set<CardInfo>> getPool(@PathVariable Long roomId, @RequestParam Long gameId, @RequestParam int capacity) {
//        return cardService.ensurePool(roomId, capacity);
//    }
//
//    @PostMapping("/{roomId}/regenerate")
//    public Mono<Set<CardInfo>> regenPool(@PathVariable Long roomId, @RequestParam Long gameId, @RequestParam int capacity) {
//        return cardService.regenerate(roomId, capacity)
//                .flatMap(cards -> publisher.publishEvent(RedisKeys.roomChannel(roomId), Map.of("type", "cardPoolGenerated", "cards", cards)).thenReturn(cards));
//    }
}
