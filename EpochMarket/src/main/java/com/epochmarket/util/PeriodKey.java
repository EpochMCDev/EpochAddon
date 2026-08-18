package com.epochmarket.util;

import java.time.LocalDate;

public final class PeriodKey {
    private PeriodKey() {
    }

    public static String daily(LocalDate date) {
        return date.toString();
    }

    public static String biDaily(LocalDate date) {
        return "b" + Math.floorDiv(date.toEpochDay(), 2);
    }

    public static String cycle(LocalDate date, int days) {
        if (days < 1) {
            throw new IllegalArgumentException("cycle days must be positive");
        }
        return "c" + days + "-" + Math.floorDiv(date.toEpochDay(), days);
    }
}
