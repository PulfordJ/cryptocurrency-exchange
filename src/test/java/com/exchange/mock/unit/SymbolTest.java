package com.exchange.mock.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.exchange.mock.domain.Symbol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pure unit tests for {@link Symbol#parse}, covering its validation branches. */
class SymbolTest {

    @Test
    @DisplayName("a well-formed BASE-QUOTE symbol parses into its parts")
    void parsesValidSymbol() {
        Symbol symbol = Symbol.parse("BTC-USD");

        assertThat(symbol.name()).isEqualTo("BTC-USD");
        assertThat(symbol.base()).isEqualTo("BTC");
        assertThat(symbol.quote()).isEqualTo("USD");
    }

    @Test
    @DisplayName("a null symbol is rejected")
    void rejectsNull() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Symbol.parse(null));
    }

    @Test
    @DisplayName("a symbol without a single dash separator is rejected")
    void rejectsWrongShape() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Symbol.parse("BTCUSD"));
    }

    @Test
    @DisplayName("a symbol with a blank base is rejected")
    void rejectsBlankBase() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Symbol.parse("-USD"));
    }

    @Test
    @DisplayName("a symbol with a blank quote is rejected")
    void rejectsBlankQuote() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Symbol.parse("BTC- "));
    }
}
