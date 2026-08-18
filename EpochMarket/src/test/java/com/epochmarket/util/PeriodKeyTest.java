package com.epochmarket.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeriodKeyTest {

    @Test
    void dailyPeriodKeyIsTheIsoDate() {
        assertEquals("2026-08-12", PeriodKey.daily(LocalDate.of(2026, 8, 12)));
    }

    @Test
    void biDailyPeriodKeyGroupsTwoConsecutiveDays() {
        assertEquals("b10338", PeriodKey.biDaily(LocalDate.of(2026, 8, 12)));
        assertEquals("b10339", PeriodKey.biDaily(LocalDate.of(2026, 8, 13)));
        assertEquals("b10339", PeriodKey.biDaily(LocalDate.of(2026, 8, 14)));
    }

    @Test
    void configurableCycleGroupsDatesByTheConfiguredLength() {
        assertEquals("c3-229", PeriodKey.cycle(LocalDate.of(1971, 11, 20), 3));
        assertEquals("c3-229", PeriodKey.cycle(LocalDate.of(1971, 11, 21), 3));
        assertEquals("c3-230", PeriodKey.cycle(LocalDate.of(1971, 11, 22), 3));
        assertEquals("c3-230", PeriodKey.cycle(LocalDate.of(1971, 11, 23), 3));
    }
}
