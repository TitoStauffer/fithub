package com.fithub.client.domain.invitation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Segredos do convite (DEV-0035): o token do link, o código curto e o hash com que são guardados. Gerados
 * com fonte criptográfica ({@link SecureRandom}); o banco guarda só o hash, e nada disso vai para log.
 */
public final class InviteSecrets {

    /** Sem 0/O e 1/I, para ninguém confundir ao digitar. */
    public static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public static final int CODE_LENGTH = 8;

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteSecrets() {}

    /** Token do link: 256 bits aleatórios em base64url. */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Código curto de 8 caracteres do alfabeto sem ambiguidade. */
    public static String newCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * Código como a pessoa digitou, sem espaços nem hífen e em maiúsculas. Vazio se não puder ser um código
     * (tamanho ou caractere fora do alfabeto).
     */
    public static Optional<String> normalizeCode(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String code = raw.replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
        if (code.length() != CODE_LENGTH || !code.chars().allMatch(c -> CODE_ALPHABET.indexOf(c) >= 0)) {
            return Optional.empty();
        }
        return Optional.of(code);
    }

    /** SHA-256 em hexadecimal: o que o banco guarda do token e do código do convite. */
    public static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", impossible);
        }
    }
}
