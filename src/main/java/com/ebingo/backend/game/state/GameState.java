package com.ebingo.backend.game.state;

import com.ebingo.backend.game.dto.CardInfo;
import com.ebingo.backend.game.enums.GameStatus;
import lombok.Data;
import lombok.ToString;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the state of a single Bingo game in a room.
 */
@Data
@ToString
public class GameState {

    private Long gameId;
    private Long roomId;

    // Players in the game
    private final Set<String> joinedPlayers = ConcurrentHashMap.newKeySet();

    // Numbers that have been drawn in the game (order matters)
    private final Set<Integer> drawnNumbers = new LinkedHashSet<>();

    // Users disqualified due to false bingo claims
    private final Set<String> disqualifiedUsers = ConcurrentHashMap.newKeySet();

    // Read from Player State
    private Set<String> userSelectedCardsIds = new LinkedHashSet<>();
    private Set<String> allSelectedCardsIds = new LinkedHashSet<>();

    // Card Pool
    private final List<CardInfo> currentCardPool = new ArrayList<>();
    private final List<CardInfo> nextCardPool = new ArrayList<>();
    private Set<String> allCardIds = new HashSet<>();

    // Game status flags
    private volatile boolean started = false;       // only one writer -> fine as volatile
    private volatile boolean ended = false;
    private volatile GameStatus status = GameStatus.READY;

    private Boolean stopNumberDrawing = false;
    private Boolean claimRequested = false;

    private Instant countdownEndTime;
    private Double commissionRate = 0.0;
    private Double entryFee = 0.0;
    private Integer capacity = 0;

    public void setJoinedPlayers(Set<String> userIds) {
        joinedPlayers.clear();
        joinedPlayers.addAll(userIds);
    }

//    public void setAllCardIds(Set<String> cardIds) {
//        allCardIds.clear();
//        allCardIds.addAll(cardIds);
//    }


    public void setDrawnNumber(LinkedHashSet<Integer> nums) {
        drawnNumbers.clear();
        drawnNumbers.addAll(nums);
    }

    /**
     * Disqualify a player.
     */
    public void disqualifyPlayer(String userId) {
        disqualifiedUsers.add((userId));
    }

    public void setDisqualifiedPlayers(Set<String> userIds) {
        disqualifiedUsers.clear();
        disqualifiedUsers.addAll(userIds);
    }

    /**
     * Set the card pool (replaces existing pool).
     */
    public Mono<Void> setCurrentCardPool(List<CardInfo> newPool) {
        return Mono.fromRunnable(() -> {
            currentCardPool.clear();
            currentCardPool.addAll(newPool);
        });
    }

//    public void setUserSelectedCardsIds(Set<String> cardIds) {
//        userSelectedCardsIds.clear();
//        userSelectedCardsIds.addAll(cardIds);
//    }

}
