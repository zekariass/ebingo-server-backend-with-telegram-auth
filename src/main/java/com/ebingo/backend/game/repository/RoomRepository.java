package com.ebingo.backend.game.repository;

import com.ebingo.backend.game.entity.Room;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface RoomRepository extends ReactiveCrudRepository<Room, Long> {
}
