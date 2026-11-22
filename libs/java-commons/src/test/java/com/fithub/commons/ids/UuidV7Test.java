package com.fithub.commons.ids;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void hasVersion7TheRfcVariantAndTheClockMillisInTheFirst48Bits() {
        Instant now = Instant.parse("2026-11-22T05:30:00.123Z");
        UUID id = UuidV7.next(Clock.fixed(now, ZoneOffset.UTC));

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(now.toEpochMilli());
    }

    @Test
    void laterMillisecondsSortAfterEarlierOnes() {
        Instant now = Instant.parse("2026-11-22T05:30:00Z");
        UUID earlier = UuidV7.next(Clock.fixed(now, ZoneOffset.UTC));
        UUID later = UuidV7.next(Clock.fixed(now.plusMillis(1), ZoneOffset.UTC));

        assertThat(later.toString()).isGreaterThan(earlier.toString());
    }

    @Test
    void idsInTheSameMillisecondAreDistinct() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-22T05:30:00Z"), ZoneOffset.UTC);
        assertThat(UuidV7.next(clock)).isNotEqualTo(UuidV7.next(clock));
    }
}
