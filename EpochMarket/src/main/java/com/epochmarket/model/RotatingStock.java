package com.epochmarket.model;

import com.epochmarket.util.PeriodKey;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Deterministic, replacement-free selection of candidates for configured slots. */
public record RotatingStock(int cycleDays, List<Integer> slots, List<RotatingCandidate> candidates) {
    public RotatingStock {
        if (cycleDays < 1) {
            throw new IllegalArgumentException("cycle days must be positive");
        }
        slots = List.copyOf(slots);
        candidates = List.copyOf(candidates);
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("rotating stock needs at least one slot");
        }
        if (candidates.size() < slots.size()) {
            throw new IllegalArgumentException("rotating stock needs at least one candidate per slot");
        }
    }

    public String cycleKey(LocalDate date) {
        return PeriodKey.cycle(date, cycleDays);
    }

    public List<MarketEntry> entries(String marketId, LocalDate date) {
        String cycleKey = cycleKey(date);
        List<RotatingCandidate> selected = new ArrayList<>(candidates);
        selected.sort(Comparator.comparing(RotatingCandidate::id));
        Collections.shuffle(selected, new Random(seed(marketId, cycleKey)));
        List<MarketEntry> entries = new ArrayList<>(slots.size());
        for (int index = 0; index < slots.size(); index++) {
            entries.add(selected.get(index).toEntry(slots.get(index), cycleKey));
        }
        return List.copyOf(entries);
    }

    private static long seed(String marketId, String cycleKey) {
        long value = 0xcbf29ce484222325L;
        String input = marketId + "\u0000" + cycleKey;
        for (int index = 0; index < input.length(); index++) {
            value ^= input.charAt(index);
            value *= 0x100000001b3L;
        }
        return value;
    }
}
