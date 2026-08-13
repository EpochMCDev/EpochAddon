package com.epochmarket.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class QuotaRepository implements AutoCloseable {
    private final File databaseFile;
    private final ExecutorService databaseExecutor;
    private Connection connection;
    private volatile boolean ready;

    public QuotaRepository(File databaseFile) {
        this.databaseFile = databaseFile;
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "EpochMarket-Database");
            thread.setDaemon(true);
            return thread;
        };
        this.databaseExecutor = Executors.newSingleThreadExecutor(threadFactory);
    }

    public CompletableFuture<Void> initialize() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        databaseExecutor.execute(() -> {
            try {
                connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
                connection.setAutoCommit(true);
                createSchema();
                ready = true;
                result.complete(null);
            } catch (Throwable exception) {
                result.completeExceptionally(exception);
            }
        });
        return result;
    }

    public boolean isReady() {
        return ready;
    }

    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS daily_quotas (
                        player_uuid TEXT NOT NULL,
                        market_id TEXT NOT NULL,
                        entry_id TEXT NOT NULL,
                        quota_date TEXT NOT NULL,
                        sold INTEGER NOT NULL CHECK (sold >= 0),
                        PRIMARY KEY (player_uuid, market_id, entry_id, quota_date)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_daily_quotas_date ON daily_quotas (quota_date)");
        }
    }

    public CompletableFuture<Integer> sold(UUID playerId, String marketId, String entryId, LocalDate date) {
        return submit(() -> soldInternal(playerId, marketId, entryId, date));
    }

    private int soldInternal(UUID playerId, String marketId, String entryId, LocalDate date) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sold FROM daily_quotas
                WHERE player_uuid = ? AND market_id = ? AND entry_id = ? AND quota_date = ?
                """)) {
            bindKey(statement, playerId, marketId, entryId, date);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    /** Atomically adds a sale only if it remains at or below the supplied limit. */
    public CompletableFuture<Boolean> reserve(UUID playerId, String marketId, String entryId, LocalDate date,
                                               int amount, int limit) {
        return submit(() -> reserveInternal(playerId, marketId, entryId, date, amount, limit));
    }

    private boolean reserveInternal(UUID playerId, String marketId, String entryId, LocalDate date,
                                    int amount, int limit) throws SQLException {
        if (amount <= 0 || limit < 0) {
            return false;
        }
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int current = soldInternal(playerId, marketId, entryId, date);
            if (current > limit - amount) {
                connection.rollback();
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO daily_quotas (player_uuid, market_id, entry_id, quota_date, sold)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(player_uuid, market_id, entry_id, quota_date)
                    DO UPDATE SET sold = excluded.sold
                    """)) {
                bindKey(statement, playerId, marketId, entryId, date);
                statement.setInt(5, current + amount);
                statement.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public CompletableFuture<Void> release(UUID playerId, String marketId, String entryId, LocalDate date, int amount) {
        return submit(() -> {
            releaseInternal(playerId, marketId, entryId, date, amount);
            return null;
        });
    }

    private void releaseInternal(UUID playerId, String marketId, String entryId, LocalDate date, int amount)
            throws SQLException {
        if (amount <= 0) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE daily_quotas SET sold = CASE WHEN sold > ? THEN sold - ? ELSE 0 END
                WHERE player_uuid = ? AND market_id = ? AND entry_id = ? AND quota_date = ?
                """)) {
            statement.setInt(1, amount);
            statement.setInt(2, amount);
            statement.setString(3, playerId.toString());
            statement.setString(4, marketId);
            statement.setString(5, entryId);
            statement.setString(6, date.toString());
            statement.executeUpdate();
        }
    }

    public CompletableFuture<Void> reset(UUID playerId, String marketId, String entryId, LocalDate date) {
        return submit(() -> {
            resetInternal(playerId, marketId, entryId, date);
            return null;
        });
    }

    private void resetInternal(UUID playerId, String marketId, String entryId, LocalDate date) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM daily_quotas
                WHERE player_uuid = ? AND market_id = ? AND entry_id = ? AND quota_date = ?
                """)) {
            bindKey(statement, playerId, marketId, entryId, date);
            statement.executeUpdate();
        }
    }

    public CompletableFuture<Void> deleteBefore(LocalDate date) {
        return submit(() -> {
            deleteBeforeInternal(date);
            return null;
        });
    }

    private void deleteBeforeInternal(LocalDate date) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM daily_quotas WHERE quota_date < ?")) {
            statement.setString(1, date.toString());
            statement.executeUpdate();
        }
    }

    private static void bindKey(PreparedStatement statement, UUID playerId, String marketId, String entryId,
                                LocalDate date) throws SQLException {
        statement.setString(1, playerId.toString());
        statement.setString(2, marketId);
        statement.setString(3, entryId);
        statement.setString(4, date.toString());
    }

    @Override
    public void close() {
        ready = false;
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            databaseExecutor.shutdownNow();
        }
        try {
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } catch (SQLException ignored) {
            // The server is shutting down and there is no caller to recover this connection.
        }
    }

    private <T> CompletableFuture<T> submit(SqlOperation<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            databaseExecutor.execute(() -> {
                try {
                    if (connection == null) {
                        throw new SQLException("The EpochMarket database is not initialized.");
                    }
                    result.complete(operation.run());
                } catch (Throwable exception) {
                    result.completeExceptionally(exception);
                }
            });
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
        }
        return result;
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T run() throws SQLException;
    }
}
