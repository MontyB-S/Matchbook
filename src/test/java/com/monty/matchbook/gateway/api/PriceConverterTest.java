package com.monty.matchbook.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PriceConverterTest {

    private final PriceConverter penny = new PriceConverter(new BigDecimal("0.01"));
    private final PriceConverter quarter = new PriceConverter(new BigDecimal("0.25"));

    @Nested
    class ToTicks {

        @Test
        void convertsAPriceToWholeTicks() {
            assertThat(penny.toTicks(new BigDecimal("1.52"))).isEqualTo(152);
        }

        @Test
        void oneTickIsOne() {
            assertThat(penny.toTicks(new BigDecimal("0.01"))).isEqualTo(1);
        }

        @Test
        void trailingZerosDoNotChangeTheResult() {
            assertThat(penny.toTicks(new BigDecimal("1.5"))).isEqualTo(150);
            assertThat(penny.toTicks(new BigDecimal("1.50"))).isEqualTo(150);
            assertThat(penny.toTicks(new BigDecimal("1.500"))).isEqualTo(150);
        }

        @Test
        void wholeNumbersConvert() {
            assertThat(penny.toTicks(new BigDecimal("100"))).isEqualTo(10_000);
        }

        @Test
        void zeroConverts() {
            assertThat(penny.toTicks(BigDecimal.ZERO)).isZero();
        }

        @Test
        void aPriceFinerThanOneTickIsRejected() {
            assertThatExceptionOfType(InvalidPriceException.class)
                    .isThrownBy(() -> penny.toTicks(new BigDecimal("0.015")));
        }

        @Test
        void aPriceMuchFinerThanOneTickIsRejected() {
            assertThatExceptionOfType(InvalidPriceException.class)
                    .isThrownBy(() -> penny.toTicks(new BigDecimal("0.001")));
        }

        @Test
        void aPriceTooLargeForALongIsRejected() {
            assertThatExceptionOfType(InvalidPriceException.class)
                    .isThrownBy(() -> penny.toTicks(new BigDecimal("100000000000000000000")));
        }

        @Test
        void nullIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> penny.toTicks(null));
        }
    }

    @Nested
    class ToDecimal {

        @Test
        void convertsTicksBackToAPrice() {
            assertThat(penny.toDecimal(152)).isEqualTo(new BigDecimal("1.52"));
        }

        @Test
        void oneTick() {
            assertThat(penny.toDecimal(1)).isEqualTo(new BigDecimal("0.01"));
        }

        @Test
        void zero() {
            assertThat(penny.toDecimal(0)).isEqualTo(new BigDecimal("0.00"));
        }

        @Test
        void scaleMatchesTheTickSize() {
            assertThat(penny.toDecimal(10_000)).isEqualTo(new BigDecimal("100.00"));
        }
    }

    @Nested
    class NonDecimalTickSize {

        @Test
        void quarterTicksConvert() {
            assertThat(quarter.toTicks(new BigDecimal("100.75"))).isEqualTo(403);
        }

        @Test
        void aPriceOffTheQuarterTickIsRejected() {
            assertThatExceptionOfType(InvalidPriceException.class)
                    .isThrownBy(() -> quarter.toTicks(new BigDecimal("100.10")));
        }

        @Test
        void quarterTicksConvertBack() {
            assertThat(quarter.toDecimal(403)).isEqualTo(new BigDecimal("100.75"));
        }
    }

    @Nested
    class RoundTrip {

        @Test
        void pennyPricesSurviveARoundTrip() {
            for (String price : new String[] {"0.01", "1.52", "99.99", "100.00", "12345.67"}) {
                BigDecimal original = new BigDecimal(price);

                assertThat(penny.toDecimal(penny.toTicks(original))).isEqualTo(original);
            }
        }

        @Test
        void quarterPricesSurviveARoundTrip() {
            for (String price : new String[] {"0.25", "100.75", "4200.50"}) {
                BigDecimal original = new BigDecimal(price);

                assertThat(quarter.toDecimal(quarter.toTicks(original))).isEqualTo(original);
            }
        }
    }

    @Nested
    class Construction {

        @Test
        void nullTickSizeIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> new PriceConverter(null));
        }

        @Test
        void zeroTickSizeIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> new PriceConverter(BigDecimal.ZERO));
        }

        @Test
        void negativeTickSizeIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> new PriceConverter(new BigDecimal("-0.01")));
        }
    }
}
