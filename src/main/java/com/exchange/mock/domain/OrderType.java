package com.exchange.mock.domain;

/**
 * Supported order types.
 *
 * <ul>
 *   <li>{@code LIMIT} – rests on the book at a fixed price; reserves funds on entry.</li>
 *   <li>{@code MARKET} – executes against available liquidity immediately (immediate-or-cancel
 *       semantics); any unfilled remainder is cancelled rather than rested.</li>
 * </ul>
 */
public enum OrderType {
    LIMIT,
    MARKET
}
