package com.ebingo.backend.game.service;

import com.ebingo.backend.game.enums.BingoColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BingoPatternVerifierTest {

    private BingoPatternVerifier verifier;
    private Map<BingoColumn, List<Integer>> card;

    @BeforeEach
    void setUp() {
        verifier = new BingoPatternVerifier();

        // Example BINGO card data (without free space)
        card = new EnumMap<>(BingoColumn.class);
        card.put(BingoColumn.B, Arrays.asList(1, 2, 3, 4, 5));
        card.put(BingoColumn.I, Arrays.asList(16, 17, 18, 19, 20));
        card.put(BingoColumn.N, Arrays.asList(31, 32, 33, 34, 35)); // middle should become 0
        card.put(BingoColumn.G, Arrays.asList(46, 47, 48, 49, 50));
        card.put(BingoColumn.O, Arrays.asList(61, 62, 63, 64, 65));
    }

    @Test
    void testFreeSpaceIsAutomaticallySetToZero() {
        var grid = getGrid(card);
        assertEquals(0, grid.get(2).get(2),
                "Middle space (row 2, col 2) should be 0 (free space)");
    }

    @Test
    void testFreeSpaceCountsAsMarkedInRow() {
        // Mark all numbers in the 3rd row except the middle
        Set<Integer> marked = new HashSet<>(Arrays.asList(3, 18, 48, 63));

        // Should still count as a completed row because center (0) is free
        assertTrue(verifier.verifyAnyRow(card, marked),
                "Row should be valid even if middle number isn't marked (free space)");
    }

    @Test
    void testFreeSpaceCountsAsMarkedInDiagonal() {
        // Mark diagonal excluding the center
        Set<Integer> marked = new HashSet<>(Arrays.asList(1, 17, 49, 65));

        assertTrue(verifier.verifyDiagonal(card, marked),
                "Diagonal should be valid because free space is automatically marked");
    }

    @Test
    void testFullHouseRequiresAllNumbersExceptFreeSpace() {
        // Mark all except one non-free number
        Set<Integer> marked = new HashSet<>();
        for (List<Integer> nums : card.values()) marked.addAll(nums);
        marked.remove(65); // Leave one number unmarked

        assertFalse(verifier.verifyFullHouse(card, marked),
                "Full house should fail if any non-free number is unmarked");

        // Now mark all
        marked.add(65);
        assertTrue(verifier.verifyFullHouse(card, marked),
                "Full house should pass when all numbers are marked");
    }

    // Utility to access private grid builder (via reflection)
    private List<List<Integer>> getGrid(Map<BingoColumn, List<Integer>> card) {
        try {
            var method = BingoPatternVerifier.class.getDeclaredMethod("toGrid", Map.class);
            method.setAccessible(true);
            //noinspection unchecked
            return (List<List<Integer>>) method.invoke(verifier, card);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke toGrid()", e);
        }
    }
}
