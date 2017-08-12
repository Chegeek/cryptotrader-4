package com.after_sunrise.cryptocurrency.cryptotrader.core;

import lombok.Getter;

/**
 * @author takanori.takase
 * @version 0.0.1
 */
public enum PropertyType {

    VERSION,

    TRADING_INTERVAL,

    TRADING_TARGETS,

    TRADING_AGGRESSIVENESS;

    private static final String PREFIX = "cryptotrader.";

    @Getter
    private final String key;

    PropertyType() {
        this.key = PREFIX + name().toLowerCase();
    }

}