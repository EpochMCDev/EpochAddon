package com.epochmarket.service;

import java.math.BigDecimal;

public record SaleResult(Status status, int amount, BigDecimal money) {
    public enum Status {
        SUCCESS,
        UNAVAILABLE,
        INVALID_AMOUNT,
        NO_ITEMS,
        NO_QUOTA,
        CHANGED,
        STORAGE_FAILURE,
        PAYOUT_FAILURE
    }

    public static SaleResult failure(Status status) {
        return new SaleResult(status, 0, BigDecimal.ZERO);
    }
}

