package com.epochmarket.util;

import java.time.LocalDate;

public final class PeriodKey {
    private PeriodKey() {
    }

    public static String daily(LocalDate date) {
        return date.toString();
    }

    public static String biDaily(LocalDate date) {
        return "b" + (date.toEpochDay() / 2);
    }
}
