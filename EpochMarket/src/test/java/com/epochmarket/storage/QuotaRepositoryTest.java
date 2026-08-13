package com.epochmarket.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotaRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void quotaIsScopedToPlayerEntryMarketAndDate() throws Exception {
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 12);
        try (QuotaRepository quotas = repository()) {
            assertTrue(await(quotas.reserve(firstPlayer, "minerals", "iron", today, 4, 10)));
            assertEquals(4, await(quotas.sold(firstPlayer, "minerals", "iron", today)));
            assertEquals(0, await(quotas.sold(secondPlayer, "minerals", "iron", today)));
            assertEquals(0, await(quotas.sold(firstPlayer, "plants", "iron", today)));
            assertEquals(0, await(quotas.sold(firstPlayer, "minerals", "iron", today.plusDays(1))));
        }
    }

    @Test
    void reservationNeverExceedsDailyLimitAndCanBeReleased() throws Exception {
        UUID player = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 12);
        try (QuotaRepository quotas = repository()) {
            assertTrue(await(quotas.reserve(player, "minerals", "iron", today, 8, 10)));
            assertFalse(await(quotas.reserve(player, "minerals", "iron", today, 3, 10)));
            assertEquals(8, await(quotas.sold(player, "minerals", "iron", today)));
            await(quotas.release(player, "minerals", "iron", today, 3));
            assertTrue(await(quotas.reserve(player, "minerals", "iron", today, 5, 10)));
            assertEquals(10, await(quotas.sold(player, "minerals", "iron", today)));
        }
    }

    @Test
    void resetAndRetentionDeleteOnlyTheExpectedRows() throws Exception {
        UUID player = UUID.randomUUID();
        LocalDate oldDate = LocalDate.of(2026, 7, 1);
        LocalDate currentDate = LocalDate.of(2026, 8, 12);
        try (QuotaRepository quotas = repository()) {
            assertTrue(await(quotas.reserve(player, "minerals", "iron", oldDate, 1, 10)));
            assertTrue(await(quotas.reserve(player, "minerals", "iron", currentDate, 5, 10)));
            await(quotas.deleteBefore(currentDate));
            assertEquals(0, await(quotas.sold(player, "minerals", "iron", oldDate)));
            assertEquals(5, await(quotas.sold(player, "minerals", "iron", currentDate)));
            await(quotas.reset(player, "minerals", "iron", currentDate));
            assertEquals(0, await(quotas.sold(player, "minerals", "iron", currentDate)));
        }
    }

    private QuotaRepository repository() throws Exception {
        QuotaRepository repository = new QuotaRepository(temporaryDirectory.resolve("market.db").toFile());
        await(repository.initialize());
        return repository;
    }

    private static <T> T await(java.util.concurrent.CompletableFuture<T> future) throws Exception {
        return future.get();
    }
}
