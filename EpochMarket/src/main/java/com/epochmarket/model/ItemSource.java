package com.epochmarket.model;

import java.util.Locale;

public enum ItemSource {
    VANILLA,
    CRAFT_ENGINE,
    SLIMEFUN;

    public static ItemSource parse(String value) {
        return ItemSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}

