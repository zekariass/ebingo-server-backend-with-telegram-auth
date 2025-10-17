package com.ebingo.backend.game.mappers;

import com.ebingo.backend.game.dto.RoomCreateDto;
import com.ebingo.backend.game.dto.RoomDto;
import com.ebingo.backend.game.dto.RoomUpdateDto;
import com.ebingo.backend.game.entity.Room;
import com.ebingo.backend.game.enums.RoomStatus;


public class RoomMapper {

    public static RoomDto toDto(Room room) {
        return RoomDto.builder()
                .id(room.getId())
                .name(room.getName())
                .capacity(room.getCapacity())
                .minPlayers(room.getMinPlayers())
                .entryFee(room.getEntryFee())
                .pattern(room.getPattern())
                .status(room.getStatus())
                .createdBy(room.getCreatedBy())
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .build();
    }

    public static Room toEntity(RoomCreateDto roomDto) {

        Room room = new Room();
        room.setName(roomDto.getName());
        room.setCapacity(roomDto.getCapacity());
        room.setMinPlayers(roomDto.getMinPlayers());
        room.setEntryFee(roomDto.getEntryFee());
        room.setPattern(roomDto.getPattern());
        room.setStatus(RoomStatus.OPEN);

        return room;
    }

    public static Room toEntity(RoomUpdateDto roomDto, Room existingRoom) {

        existingRoom.setId(roomDto.getId());
        existingRoom.setName(roomDto.getName());
        existingRoom.setCapacity(roomDto.getCapacity());
        existingRoom.setMinPlayers(roomDto.getMinPlayers());
        existingRoom.setEntryFee(roomDto.getEntryFee());
        existingRoom.setPattern(roomDto.getPattern());
        existingRoom.setStatus(roomDto.getStatus());

        return existingRoom;
    }
}
