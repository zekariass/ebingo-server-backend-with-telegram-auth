package com.ebingo.backend.game.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class GameEndResponse {
    private Long gameId;
    private Long playerId;
    private String playerName;
    private String cardId;
    private String pattern;
    private BigDecimal prizeAmount;
    private LocalDateTime winAt;
    private boolean hasWinner;
    private Set<Integer> markedNumbers;
    private CardInfo card;
}
