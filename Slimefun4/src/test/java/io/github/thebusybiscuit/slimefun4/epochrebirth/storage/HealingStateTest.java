package io.github.thebusybiscuit.slimefun4.epochrebirth.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.thebusybiscuit.slimefun4.epochrebirth.healing.HealingServiceKt;
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.HealingTier;
import org.junit.jupiter.api.Test;

class HealingStateTest {

    @Test
    void levelOneRunsFiveThirtySixSecondRecoveries() {
        var state = HealingState.Companion.create(HealingTier.I, 0L, true);

        assertEquals(180_000L, state.remainingMillis(0L));
        assertEquals(0, state.advance(35_999L, Integer.MAX_VALUE).getDueRecoveries());

        var first = state.advance(36_000L, Integer.MAX_VALUE);
        assertEquals(1, first.getDueRecoveries());
        assertNotNull(first.getNextState());
        assertEquals(4, first.getNextState().getRecoveriesLeft());
        assertEquals(72_000L, first.getNextState().getNextRecoveryAt());

        var finished = state.advance(180_000L, Integer.MAX_VALUE);
        assertEquals(5, finished.getDueRecoveries());
        assertNull(finished.getNextState());
    }

    @Test
    void pausesCountdownWhileHealthPenaltyIsEmpty() {
        var state = HealingState.Companion.create(HealingTier.I, 0L, true);
        var paused = state.pause(18_000L);

        assertNull(paused.getNextRecoveryAt());
        assertEquals(18_000L, paused.getRemainingDelayMillis());
        assertEquals(162_000L, paused.remainingMillis(18_000L));
        assertEquals(162_000L, paused.remainingMillis(1_000_000L));

        var resumed = paused.resume(1_000_000L);
        assertEquals(1_018_000L, resumed.getNextRecoveryAt());
    }

    @Test
    void appendsMixedArrowTiersWithoutLosingTheirIntervals() {
        var state = HealingState.Companion.create(HealingTier.I, 0L, false).append(HealingTier.II, 5);

        assertEquals(10, state.getRecoveriesLeft());
        assertEquals(2, state.getBatches().size());
        assertEquals(330_000L, state.remainingMillis(0L));
        assertEquals(180_000L, state.remainingMillis(HealingTier.I, 0L));
        assertEquals(150_000L, state.remainingMillis(HealingTier.II, 0L));
        assertEquals(HealingTier.I, state.getTier());
    }

    @Test
    void keepsLevelOneIntervalsWhenAddedAfterLevelTwo() {
        var state = HealingState.Companion.create(HealingTier.II, 0L, true).append(HealingTier.I, 5);

        assertEquals(150_000L, state.remainingMillis(HealingTier.II, 0L));
        assertEquals(180_000L, state.remainingMillis(HealingTier.I, 0L));

        var afterLevelTwo = state.advance(150_000L, Integer.MAX_VALUE);
        assertEquals(5, afterLevelTwo.getDueRecoveries());
        assertNotNull(afterLevelTwo.getNextState());
        assertEquals(HealingTier.I, afterLevelTwo.getNextState().getTier());
        assertEquals(186_000L, afterLevelTwo.getNextState().getNextRecoveryAt());
    }

    @Test
    void keepsLevelTwoIntervalsWhenAddedAfterLevelOne() {
        var state = HealingState.Companion.create(HealingTier.I, 0L, true).append(HealingTier.II, 5);

        var afterLevelOne = state.advance(180_000L, Integer.MAX_VALUE);
        assertEquals(5, afterLevelOne.getDueRecoveries());
        assertNotNull(afterLevelOne.getNextState());
        assertEquals(HealingTier.II, afterLevelOne.getNextState().getTier());
        assertEquals(210_000L, afterLevelOne.getNextState().getNextRecoveryAt());
    }

    @Test
    void preservesUnusedRecoveriesAfterCatchingUpToFullHealth() {
        var state = HealingState.Companion.create(HealingTier.II, 0L, true);

        var advance = state.advance(150_000L, 2);
        assertEquals(2, advance.getDueRecoveries());
        assertNotNull(advance.getNextState());
        assertEquals(3, advance.getNextState().getRecoveriesLeft());

        var paused = advance.getNextState().pause(150_000L);
        assertNull(paused.getNextRecoveryAt());
        assertEquals(90_000L, paused.remainingMillis(150_000L));
    }

    @Test
    void formatsCountdownWithCeilingSeconds() {
        assertEquals("3:00", HealingServiceKt.formatHealingDuration(180_000L));
        assertEquals("2:30", HealingServiceKt.formatHealingDuration(150_000L));
        assertEquals("0:01", HealingServiceKt.formatHealingDuration(1L));
        assertEquals("0:00", HealingServiceKt.formatHealingDuration(0L));
    }
}
