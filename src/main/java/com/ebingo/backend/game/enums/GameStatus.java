package com.ebingo.backend.game.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GameStatus {
    READY,
    COUNTDOWN,
    PLAYING,
    COMPLETED,
    CANCELLED_ADMIN,
    CANCELLED_NO_MIN_PLAYERS;

    @JsonCreator
    public static GameStatus from(String value) {
        switch (value.toUpperCase()) {
            case "READY":
                return READY;
            case "PLAYING":
                return PLAYING;
            case "COMPLETED":
                return COMPLETED;
            case "CANCELLED_ADMIN":
                return CANCELLED_ADMIN;
            case "CANCELLED_NO_MIN_PLAYERS":
                return CANCELLED_NO_MIN_PLAYERS;
            default:
                throw new IllegalArgumentException("Unknown GameStatus: " + value);
        }
    }

    @JsonValue
    public String toValue() {
        switch (this) {
            case READY:
                return "READY";
            case PLAYING:
                return "PLAYING";
            case COMPLETED:
                return "COMPLETED";
            case CANCELLED_ADMIN:
                return "CANCELLED_ADMIN";
            case CANCELLED_NO_MIN_PLAYERS:
                return "CANCELLED_NO_MIN_PLAYERS";
            default:
                throw new IllegalArgumentException();
        }
    }
}
