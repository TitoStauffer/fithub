package com.fithub.commons.ids;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * UUID versão 7 (RFC 9562): 48 bits de milissegundos desde a época, seguidos de bits aleatórios.
 * Ordena por tempo de criação e é a convenção de ids do FitHub (ENGENHARIA.md, seção 5).
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID next() {
        return next(Clock.systemUTC());
    }

    public static UUID next(Clock clock) {
        long millis = clock.millis();
        long randomA = RANDOM.nextLong();
        long randomB = RANDOM.nextLong();
        long mostSignificant = (millis << 16) | 0x7000L | (randomA & 0x0FFFL);
        long leastSignificant = (randomB & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
