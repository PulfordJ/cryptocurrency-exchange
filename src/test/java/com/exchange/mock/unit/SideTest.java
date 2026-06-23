package com.exchange.mock.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.exchange.mock.domain.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pure unit test for {@link Side#opposite}. */
class SideTest {

    @Test
    @DisplayName("opposite flips BUY and SELL")
    void oppositeFlipsSide() {
        assertThat(Side.BUY.opposite()).isEqualTo(Side.SELL);
        assertThat(Side.SELL.opposite()).isEqualTo(Side.BUY);
    }
}
