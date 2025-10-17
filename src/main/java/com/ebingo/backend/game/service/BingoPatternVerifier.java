package com.ebingo.backend.game.service;

import com.ebingo.backend.game.enums.BingoColumn;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class BingoPatternVerifier {

    /**
     * Flatten the card numbers into a 2D grid (5x5) in row-major order.
     * Center free space (0) is included.
     */
    private List<List<Integer>> toGrid(Map<BingoColumn, List<Integer>> card) {
        List<List<Integer>> grid = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            grid.add(new ArrayList<>(5));
        }

        List<BingoColumn> orderedColumns = Arrays.asList(BingoColumn.values()); // B, I, N, G, O

        for (int colIndex = 0; colIndex < 5; colIndex++) {
            BingoColumn col = orderedColumns.get(colIndex);
            List<Integer> numbersInCol = card.get(col);

            for (int rowIndex = 0; rowIndex < 5; rowIndex++) {
                int number = numbersInCol.get(rowIndex);
                grid.get(rowIndex).add(number);
            }
        }

        // Ensure center free space is 0
        grid.get(2).set(2, 0);

        return grid;
    }

    /**
     * Checks if all elements in 'subset' are in 'marked', treating 0 (free space) as always marked.
     */
    private boolean containsAll(Collection<Integer> subset, Set<Integer> marked) {
        Set<Integer> effectiveMarked = new HashSet<>(marked);
        effectiveMarked.add(0); // free space is always counted
        return effectiveMarked.containsAll(subset);
    }

    // -------------------------
    // Standard patterns
    // -------------------------

    public boolean verifyFullHouse(Map<BingoColumn, List<Integer>> card, Set<Integer> marked) {
        List<List<Integer>> grid = toGrid(card);
        List<Integer> allNumbers = grid.stream()
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return containsAll(allNumbers, marked);
    }

    public boolean verifyAnyRow(Map<BingoColumn, List<Integer>> card, Set<Integer> marked) {
        List<List<Integer>> grid = toGrid(card);
        return grid.stream().anyMatch(row -> containsAll(row, marked));
    }

    public boolean verifyAnyColumn(Map<BingoColumn, List<Integer>> card, Set<Integer> marked) {
        List<List<Integer>> grid = toGrid(card);
        for (int col = 0; col < 5; col++) {
            int finalCol = col;
            List<Integer> column = grid.stream().map(row -> row.get(finalCol)).collect(Collectors.toList());
            if (containsAll(column, marked)) return true;
        }
        return false;
    }

    public boolean verifyDiagonal(Map<BingoColumn, List<Integer>> card, Set<Integer> marked) {
        List<List<Integer>> grid = toGrid(card);
        boolean mainDiag = true;
        boolean antiDiag = true;

        for (int i = 0; i < 5; i++) {
            if (!containsAll(Collections.singletonList(grid.get(i).get(i)), marked)) mainDiag = false;
            if (!containsAll(Collections.singletonList(grid.get(i).get(4 - i)), marked)) antiDiag = false;
        }

        return mainDiag || antiDiag;
    }

    public boolean verifyFourCorners(Map<BingoColumn, List<Integer>> card, Set<Integer> marked) {
        List<List<Integer>> grid = toGrid(card);
        List<Integer> corners = Arrays.asList(
                grid.get(0).get(0),
                grid.get(0).get(4),
                grid.get(4).get(0),
                grid.get(4).get(4)
        );
        return containsAll(corners, marked);
    }

    // -------------------------
    // Composite patterns
    // -------------------------

    public boolean verifyLine(Map<BingoColumn, List<Integer>> card, Set<Integer> marked) {
        return verifyAnyRow(card, marked) || verifyAnyColumn(card, marked);
    }

    public boolean verifyLineOrFourCorners(Map<BingoColumn, List<Integer>> card, Set<Integer> marked) {
        return verifyLine(card, marked) || verifyFourCorners(card, marked);
    }

    // -------------------------
    // Generic pattern selector
    // -------------------------

    public boolean verifyPattern(Map<BingoColumn, List<Integer>> card, Set<Integer> marked, String pattern) {
        return switch (pattern.toUpperCase()) {
            case "FULL_HOUSE" -> verifyFullHouse(card, marked);
            case "ROW" -> verifyAnyRow(card, marked);
            case "COLUMN" -> verifyAnyColumn(card, marked);
            case "DIAGONAL" -> verifyDiagonal(card, marked);
            case "CORNERS" -> verifyFourCorners(card, marked);
            case "LINE" -> verifyLine(card, marked);
            case "LINE_AND_CORNERS" -> verifyLineOrFourCorners(card, marked);
            default -> throw new IllegalArgumentException("Unknown pattern: " + pattern);
        };
    }
}
